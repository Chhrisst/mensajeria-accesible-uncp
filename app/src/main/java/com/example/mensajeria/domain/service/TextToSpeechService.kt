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

    private var tts: TextToSpeech? = TextToSpeech(context, this)
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
            tts?.setSpeechRate(0.95f) // Ritmo ligeramente pausado para máxima inteligibilidad auditiva
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

    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onDone: () -> Unit = {}
    ) {
        if (text.isBlank()) return

        // Si el motor de Google aún se está conectando, guardamos el mensaje para decirlo apenas conecte
        if (!isReady) {
            pendingText = text
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
        tts?.speak(text, queueMode, params, utteranceId)
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
