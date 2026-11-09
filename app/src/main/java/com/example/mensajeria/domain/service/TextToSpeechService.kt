package com.example.mensajeria.domain.service

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Servicio encapsulado de Síntesis de Voz (Text-to-Speech) adaptado para personas invidentes.
 * Soporta cola pendiente y garantiza que los callbacks se ejecuten en el hilo principal (Main Thread).
 */
class TextToSpeechService(
    context: Context,
    private val onInitSuccess: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    init {
        tts = try {
            TextToSpeech(context, this, "com.google.android.tts")
        } catch (_: Exception) {
            TextToSpeech(context, this)
        }
    }

    private var isReady: Boolean = false
    private var pendingText: String? = null
    private var pendingOnDone: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val localePe = Locale.forLanguageTag("es-PE")
            val res = tts?.setLanguage(localePe)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.forLanguageTag("es-ES")
            }

            try {
                val availableVoices = tts?.voices?.filter { v ->
                    v.locale.language == "es" && !v.isNetworkConnectionRequired
                } ?: emptyList()
                val bestVoice = availableVoices.maxByOrNull { it.quality }
                if (bestVoice != null) {
                    tts?.voice = bestVoice
                }
            } catch (_: Exception) {}

            tts?.setPitch(1.0f)
            tts?.setSpeechRate(0.92f) // Ritmo natural y claro que respeta las pausas de comas y puntos
            isReady = true
            mainHandler.post {
                onInitSuccess()
            }

            // Reproducir cualquier anuncio pendiente que se haya solicitado mientras cargaba el motor
            val textToPlay = pendingText
            val callbackToRun = pendingOnDone
            pendingText = null
            pendingOnDone = null

            if (!textToPlay.isNullOrBlank()) {
                mainHandler.post {
                    speak(textToPlay, onDone = callbackToRun ?: {})
                }
            }
        }
    }

    /**
     * Da formato al texto para máxima inteligibilidad y entonación natural en español:
     * - Antepone signos de apertura interrogativa (¿) y exclamativa (¡) para que el motor
     *   de síntesis eleve la curva de tono melódico en preguntas.
     * - Garantiza pausas claras en comas (,), puntos (.) y dos puntos (:).
     * - Pronuncia correctamente identificadores @ como 'arroba'.
     */
    fun formatForNaturalSpeech(text: String): String {
        var formatted = text.trim()
        if (formatted.isBlank()) return ""

        // Pronunciar @ como 'arroba'
        formatted = formatted.replace("@", " arroba ")

        // Separar cláusulas para asegurar entonación de preguntas en español
        val sentences = formatted.split(Regex("(?<=[.?!\\n])\\s+"))
        formatted = sentences.joinToString(" ") { sentence ->
            var s = sentence.trim()
            if (s.endsWith("?") && !s.startsWith("¿")) {
                s = "¿$s"
            } else if (s.endsWith("!") && !s.startsWith("¡")) {
                s = "¡$s"
            }
            s
        }

        // Respetar pausas claras en signos de puntuación
        formatted = formatted
            .replace(",", ", ")
            .replace(":", ": ")
            .replace(";", "; ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return formatted
    }

    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onDone: () -> Unit = {}
    ) {
        if (text.isBlank()) return

        val formattedText = formatForNaturalSpeech(text)

        // Si el motor aún se está conectando, guardamos el mensaje para decirlo apenas conecte
        if (!isReady) {
            pendingText = formattedText
            pendingOnDone = onDone
            return
        }

        val utteranceId = "msg_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    mainHandler.post {
                        onDone()
                    }
                }
            }
            override fun onError(id: String?) {}
        })

        val params = Bundle()
        tts?.speak(formattedText, queueMode, params, utteranceId)
    }

    fun stop() {
        pendingText = null
        pendingOnDone = null
        tts?.stop()
    }

    fun shutdown() {
        pendingText = null
        pendingOnDone = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
