package com.example.mensajeria.data.api

import android.util.Log
import com.example.mensajeria.data.model.VoiceAssistantContext
import com.example.mensajeria.data.model.VoiceCommandResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
    private const val FALLBACK_MODEL = "gemini-2.5-flash"

    private const val TAG = "GeminiDirectClient"

    // callTimeout acota cada intento: sin él, primario + respaldo podían sumar ~48 s
    // con el asistente "Consultando con Inteligencia Artificial..." colgado.
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun summarize(text: String): String = withContext(Dispatchers.IO) {
        val prompt = "Eres un asistente accesible de la aplicación de mensajería. " +
                "Resume el siguiente mensaje en máximo 2 oraciones directas y claras para una persona invidente, " +
                "priorizando fechas, horas o acciones:\n\n\"$text\""
        generateContent(prompt) ?: "No se pudo generar el resumen del mensaje."
    }

    suspend fun refineSpeech(rawText: String): String = withContext(Dispatchers.IO) {
        val localPolished = com.example.mensajeria.domain.service.IntonationPunctuationPolisher.polish(rawText)
        val prompt = """
            Eres un corrector de estilo e intención para mensajes dictados por voz en español por personas con discapacidad visual.
            Tu tarea es captar la entonación e intención natural del hablante (saludos, preguntas con ¿?, exclamaciones con ¡!, comas, puntos y tildes).
            Ejemplo: si el dictado crudo es "Hola Que Tal como estas", debes devolver: "Hola, qué tal. ¿Cómo estás?"
            Ejemplo: si dice "avísame cuando llegas estoy esperando", devuelve: "Avísame cuando llegas, estoy esperando."
            Ejemplo: si dice "puedes venir mas tarde a que hora nos vemos", devuelve: "¿Puedes venir más tarde? ¿A qué hora nos vemos?"
            No inventes palabras ni cambies el mensaje. Devuelve ÚNICAMENTE el texto final corregido, sin comillas ni explicaciones.

            Dictado crudo: "$rawText"
        """.trimIndent()
        val aiResult = generateContent(prompt)?.trim()?.trim('"')
        if (!aiResult.isNullOrBlank()) {
            com.example.mensajeria.domain.service.IntonationPunctuationPolisher.polish(aiResult)
        } else {
            localPolished
        }
    }

    /**
     * Procesa comandos de voz en lenguaje natural y devuelve una acción estructurada
     * para el Asistente de Voz Accesible de la UNCP. Soporta diálogos multi-turno.
     */
    suspend fun processVoiceCommand(
        spokenText: String,
        context: VoiceAssistantContext,
        dialogueHistory: List<Pair<String, String>> = emptyList()
    ): VoiceCommandResult = withContext(Dispatchers.IO) {
        val historySection = if (dialogueHistory.isNotEmpty()) {
            "Historial previo de la conversación reciente:\n" +
            dialogueHistory.joinToString("\n") { "Usuario: \"${it.first}\"\nAsistente: \"${it.second}\"" } + "\n\n"
        } else ""

        val prompt = """
            Eres el Asistente de Voz Inteligente de la aplicación de mensajería accesible para personas con discapacidad visual.
            Cuando sea necesario referirte a la app, llámala simplemente "la aplicación de mensajería" o "Messenger".
            $historySection
            El usuario ahora te dijo: "$spokenText"

            Contexto actual de la aplicación:
            - Pantalla actual: ${context.currentScreen}
            - Usuario conectado: ${context.currentUserName} (nombre de usuario único: @${context.currentUsername})
            - Hora y fecha actual: ${context.currentTime}
            - Clima / temperatura actual: ${context.currentWeather}
            - Contactos disponibles: ${context.contacts.joinToString(", ")}
            ${if (context.activeChatContact != null) "- Chat abierto con: ${context.activeChatContact}" else ""}
            ${if (context.recentMessages.isNotEmpty()) "- Mensajes recientes: ${context.recentMessages.joinToString(" | ")}" else ""}

            REGLA CLAVE DE CONVERSACIÓN Y PREGUNTAS COMUNES:
            1. Solo responde con la hora o fecha del reloj si el usuario formula una pregunta estricta sobre la hora actual (ej: "¿qué hora es?", "¿qué hora tienes?", "dime la hora"). Si el usuario está pidiendo enviar un mensaje, dictando, o preguntando sobre una reunión (ej: "Envía un mensaje diciendo a qué hora nos encontramos", "¿a qué hora nos vemos?"), NUNCA digas la hora del reloj; interpreta la orden como acción ("SEND_MESSAGE") o como conversación natural.
            2. Si en el historial previo le preguntaste al usuario por un nombre, mensaje o dato (por ejemplo: "¿Qué mensaje deseas enviar?"), interpreta lo que el usuario acaba de decir como respuesta directa a esa pregunta.
            3. Si el usuario está dentro de un chat ("CHAT") y dicta un mensaje para enviar (por ejemplo "dile hola que tal como estas" o "envía un mensaje que diga..."), usa "action": "SEND_MESSAGE" y redacta "messageText" captando la entonación e intención con puntuación real en español (ej: "Hola, qué tal. ¿Cómo estás?").
            4. Si pide enviar una nota de voz o audio ("envía una nota de voz", "graba un audio"), usa "action": "SEND_VOICE_NOTE".
            5. Si pide resumir el último mensaje ("resume el mensaje", "resumen"), usa "action": "SUMMARIZE_MESSAGES".
            6. Si pide llamar ("llama a...", "hacer una llamada"), usa "action": "START_CALL".
            7. Si el usuario pide agregar un contacto (ej: "agrega un contacto", "quiero agregar un contacto", "agregar contacto"), NUNCA preguntes por el nombre personal de la persona. Pregunta SIEMPRE por su nombre de usuario único (@usuario o nickname) diciendo exactamente: "¿Cuál es el nombre de usuario del contacto que deseas agregar?" usando "action": "ADD_CONTACT" y "contactName": null.
            8. Si el usuario indica o dicta el nombre de usuario para agregar (ej: "juan123", "@juan123", "agrega a juan123"), usa "action": "ADD_CONTACT", coloca el nombre de usuario en "contactName" (en minúsculas, sin espacios ni arroba), y en "spokenResponse" di: "Buscando al usuario arroba [usuario] para agregarlo."

            Analiza la intención del usuario y responde ÚNICAMENTE un objeto JSON válido con este formato:
            {
              "action": "OPEN_CHAT" | "LIST_CONTACTS" | "ADD_CONTACT" | "DELETE_CONTACT" | "MODIFY_CONTACT" | "SEND_MESSAGE" | "SEND_VOICE_NOTE" | "READ_MESSAGES" | "SUMMARIZE_MESSAGES" | "START_CALL" | "LOGOUT" | "BACK" | "HELP" | "GENERAL_REPLY",
              "targetContact": "Nombre exacto o más cercano de la lista si aplica, o null",
              "contactName": "Nombre de usuario único (@usuario o nickname) en minúsculas si se pide agregar o renombrar un contacto, o null",
              "contactPhone": "Número telefónico si se dictó al agregar, o null",
              "messageText": "Mensaje bien puntuado con signos de interrogación, exclamación, comas y tildes si aplica, o null",
              "spokenResponse": "Respuesta conversacional breve, cálida, sin tecnicismos ni formato markdown, lista para ser leída por voz al usuario ciego."
            }
        """.trimIndent()

        val jsonStr = generateContent(prompt, isJson = true)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val cleanJson = jsonStr.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val obj = JSONObject(cleanJson)
                val rawMessageText = if (obj.isNull("messageText")) null else obj.optString("messageText").takeIf { it.isNotBlank() }
                val polishedMsgText = rawMessageText?.let {
                    com.example.mensajeria.domain.service.IntonationPunctuationPolisher.polish(it)
                }
                return@withContext VoiceCommandResult(
                    action = obj.optString("action", "GENERAL_REPLY"),
                    targetContact = if (obj.isNull("targetContact")) null else obj.optString("targetContact").takeIf { it.isNotBlank() },
                    contactName = if (obj.isNull("contactName")) null else obj.optString("contactName").takeIf { it.isNotBlank() },
                    contactPhone = if (obj.isNull("contactPhone")) null else obj.optString("contactPhone").takeIf { it.isNotBlank() },
                    messageText = polishedMsgText,
                    spokenResponse = obj.optString("spokenResponse", "Entendido.")
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        VoiceCommandResult(
            action = "GENERAL_REPLY",
            spokenResponse = "No logré entender la instrucción. Pulsa el botón del asistente de voz y habla después del tono."
        )
    }

    private suspend fun generateContent(prompt: String, isJson: Boolean = false): String? {
        if (API_KEY.isBlank()) {
            Log.e(TAG, "GEMINI_API_KEY vacía en local.properties: se omite la consulta a Gemini.")
            return null
        }
        val models = listOf(PRIMARY_MODEL, FALLBACK_MODEL)
        for (model in models) {
            try {
                // La clave va en cabecera (no en la URL) para que no aparezca en logs de red/proxies.
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
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
                    .header("x-goog-api-key", API_KEY)
                    .post(requestBody)
                    .build()

                // `use` cierra SIEMPRE la respuesta (antes las respuestas 4xx/5xx quedaban abiertas
                // y agotaban el pool de conexiones tras varios fallos).
                val text = httpClient.newCall(request).await().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Gemini ($model) respondió HTTP ${response.code}")
                        return@use null
                    }
                    val bodyStr = response.body?.string() ?: return@use null
                    val candidates = JSONObject(bodyStr).optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) return@use null
                    val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    if (parts == null || parts.length() == 0) return@use null
                    parts.optJSONObject(0)?.optString("text")?.trim()
                }
                if (!text.isNullOrBlank()) return text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fallo consultando Gemini ($model): ${e.javaClass.simpleName} ${e.message}")
            }
        }
        return null
    }

    /**
     * Ejecuta la llamada de forma cancelable: si la corrutina se cancela (timeout del asistente,
     * usuario que detiene la consulta), se aborta también la petición HTTP en curso.
     */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            try { cancel() } catch (_: Exception) {}
        }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }

            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        })
    }
}
