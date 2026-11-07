package com.example.mensajeria.data.api

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

    private fun generateContent(prompt: String): String? {
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
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.2)
                        put("maxOutputTokens", 250)
                    })
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
