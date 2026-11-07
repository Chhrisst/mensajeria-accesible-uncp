package com.example.mensajeria.data.api

import com.example.mensajeria.data.model.VoiceAssistantContext
import com.example.mensajeria.data.model.VoiceCommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente directo de Google Gemini AI (Capa Gratuita Oficial).
 * Proporciona respaldo inmediato y de alta velocidad para la asistencia en la app móvil.
 */
object GeminiDirectClient {

    private val API_KEY: String get() = com.example.mensajeria.BuildConfig.GEMINI_API_KEY
    private const val PRIMARY_MODEL = "gemini-flash-lite-latest"
    private const val FALLBACK_MODEL = "gemini-3.8-flash"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun summarize(text: String): String = withContext(Dispatchers.IO) {
        val prompt = "Eres un asistente accesible de la Unión Nacional de Ciegos del Perú. " +
                "Resume el siguiente mensaje en máximo 2 oraciones directas y claras para una persona invidente, " +
                "priorizando fechas, horas o acciones:\n\n\"$text\""
        generateContent(prompt) ?: "No se pudo generar el resumen del mensaje."
    }

    suspend fun refineSpeech(rawText: String): String = withContext(Dispatchers.IO) {
        val prompt = "El siguiente texto fue dictado por voz por una persona invidente. " +
                "Agrega los signos de puntuación adecuados (comas, puntos, signos de interrogación) y mayúsculas, " +
                "sin alterar el significado. Devuelve únicamente el texto corregido sin notas adicionales:\n\n\"$rawText\""
        generateContent(prompt) ?: rawText
    }

    /**
     * Procesa comandos de voz en lenguaje natural y devuelve una acción estructurada
     * para el Asistente de Voz Accesible de la UNCP.
     */
    suspend fun processVoiceCommand(
        spokenText: String,
        context: VoiceAssistantContext
    ): VoiceCommandResult = withContext(Dispatchers.IO) {
        val prompt = """
            Eres el Asistente de Voz Inteligente de la app de mensajería accesible para personas con discapacidad visual de la Unión Nacional de Ciegos del Perú (UNCP).
            El usuario te dijo: "$spokenText"

            Contexto actual de la aplicación:
            - Pantalla actual: ${context.currentScreen}
            - Usuario conectado: ${context.currentUserName}
            - Contactos disponibles: ${context.contacts.joinToString(", ")}
            ${if (context.activeChatContact != null) "- Chat abierto con: ${context.activeChatContact}" else ""}
            ${if (context.recentMessages.isNotEmpty()) "- Mensajes recientes: ${context.recentMessages.joinToString(" | ")}" else ""}

            Analiza la intención del usuario y responde ÚNICAMENTE un objeto JSON válido con este formato:
            {
              "action": "OPEN_CHAT" | "LIST_CONTACTS" | "ADD_CONTACT" | "DELETE_CONTACT" | "MODIFY_CONTACT" | "SEND_MESSAGE" | "READ_MESSAGES" | "LOGOUT" | "BACK" | "HELP" | "GENERAL_REPLY",
              "targetContact": "Nombre exacto o más cercano de la lista si aplica, o null",
              "contactName": "Nombre o identificador único (@usuario) si se pide agregar o renombrar un contacto, o null",
              "contactPhone": "Número telefónico si se dictó al agregar, o null",
              "messageText": "Mensaje para enviar si aplica, o null",
              "spokenResponse": "Respuesta conversacional breve, cálida, sin tecnicismos ni formato markdown, lista para ser leída por voz al usuario ciego."
            }
        """.trimIndent()

        val jsonStr = generateContent(prompt, isJson = true)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val cleanJson = jsonStr.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val obj = JSONObject(cleanJson)
                return@withContext VoiceCommandResult(
                    action = obj.optString("action", "GENERAL_REPLY"),
                    targetContact = if (obj.isNull("targetContact")) null else obj.optString("targetContact").takeIf { it.isNotBlank() },
                    contactName = if (obj.isNull("contactName")) null else obj.optString("contactName").takeIf { it.isNotBlank() },
                    contactPhone = if (obj.isNull("contactPhone")) null else obj.optString("contactPhone").takeIf { it.isNotBlank() },
                    messageText = if (obj.isNull("messageText")) null else obj.optString("messageText").takeIf { it.isNotBlank() },
                    spokenResponse = obj.optString("spokenResponse", "Entendido.")
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        VoiceCommandResult(
            action = "GENERAL_REPLY",
            spokenResponse = "No logré entender la instrucción. Puedes decir 'Quiénes están en pantalla', 'Entra al chat con alguien', o 'Agrega un contacto'."
        )
    }

    private fun generateContent(prompt: String, isJson: Boolean = false): String? {
        val models = listOf(PRIMARY_MODEL, FALLBACK_MODEL)
        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$API_KEY"
                val jsonPayload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    val genConfig = JSONObject().apply {
                        put("temperature", 0.2)
                        put("maxOutputTokens", 300)
                    }
                    if (isJson) {
                        genConfig.put("response_mime_type", "application/json")
                    }
                    put("generationConfig", genConfig)
                }

                val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: continue
                    val root = JSONObject(bodyStr)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text").trim()
                            if (text.isNotBlank()) return text
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }
}
