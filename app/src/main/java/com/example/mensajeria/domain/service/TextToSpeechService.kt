package com.example.mensajeria.domain.service

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Servicio encapsulado de Síntesis de Voz (Text-to-Speech) adaptado para personas invidentes.
 */
class TextToSpeechService(
    context: Context,
    private val onInitSuccess: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady: Boolean = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val localePe = Locale.forLanguageTag("es-PE")
            val res = tts?.setLanguage(localePe)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.forLanguageTag("es-ES")
            }
            tts?.setSpeechRate(0.95f) // Ritmo ligeramente pausado para máxima inteligibilidad auditiva
            isReady = true
            onInitSuccess()
        }
    }

    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onDone: () -> Unit = {}
    ) {
        if (!isReady || text.isBlank()) return

        val utteranceId = "msg_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    onDone()
                }
            }
            override fun onError(id: String?) {}
        })

        val params = Bundle()
        tts?.speak(text, queueMode, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
