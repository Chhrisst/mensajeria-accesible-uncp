package com.example.mensajeria.domain.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Servicio encapsulado de Reconocimiento de Voz (STT) adaptado para interacciones accesibles.
 * Gestiona correctamente el ciclo de vida del SpeechRecognizer entre turnos consecutivos,
 * reproduce el tono guía antes de abrir el micrófono y rescata resultados parciales en palabras cortas.
 */
class SpeechService(private val context: Context) {

    private val appContext = context.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vibrationService = VibrationService.getInstance(appContext)
    private val soundService = SoundNotificationService.getInstance(appContext)

    private var sessionGeneration = 0L
    private var lastPartialText = ""
    private var listenStartTimestamp = 0L
    private var retryCount = 0

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    private fun destroyCurrentRecognizer() {
        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SpeechService", "Aviso al detener reconocedor previo: ${e.message}")
        }
        speechRecognizer = null
    }

    fun startListening(
        onReady: () -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        playPromptTone: Boolean = true
    ) {
        val currentGen = ++sessionGeneration
        retryCount = 0

        val kickoff = Runnable {
            if (currentGen != sessionGeneration) return@Runnable
            if (!isAvailable()) {
                Log.e("SpeechService", "SpeechRecognizer no está disponible en este dispositivo.")
                onError("El reconocimiento de voz no está disponible en este dispositivo.")
                return@Runnable
            }

            val hadPrevious = speechRecognizer != null
            if (hadPrevious) {
                destroyCurrentRecognizer()
            }

            val teardownDelay = if (hadPrevious) 220L else 0L
            mainHandler.postDelayed({
                if (currentGen != sessionGeneration) return@postDelayed

                if (playPromptTone) {
                    soundService.playListeningPromptSound()
                    mainHandler.postDelayed({
                        if (currentGen == sessionGeneration) {
                            launchRecognizerSession(currentGen, onReady, onResult, onError)
                        }
                    }, 240L)
                } else {
                    launchRecognizerSession(currentGen, onReady, onResult, onError)
                }
            }, teardownDelay)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            kickoff.run()
        } else {
            mainHandler.post(kickoff)
        }
    }

    private fun launchRecognizerSession(
        expectedGen: Long,
        onReady: () -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (expectedGen != sessionGeneration) return

        destroyCurrentRecognizer()
        lastPartialText = ""
        listenStartTimestamp = System.currentTimeMillis()
        var sessionSettled = false

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-PE")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla después del tono")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (expectedGen != sessionGeneration || sessionSettled) return
                    Log.i("SpeechService", "onReadyForSpeech: Micrófono abierto y escuchando activamente.")
                    vibrationService.vibrateStartListening()
                    onReady()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (expectedGen != sessionGeneration || sessionSettled) return
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (!partial.isNullOrBlank()) {
                        lastPartialText = partial
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (expectedGen != sessionGeneration || sessionSettled) return
                    sessionSettled = true
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val recognized = matches?.firstOrNull()?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: lastPartialText.trim().takeIf { it.isNotBlank() }

                    Log.i("SpeechService", "onResults detectado: $recognized")
                    destroyCurrentRecognizer()

                    if (!recognized.isNullOrEmpty()) {
                        onResult(recognized)
                    } else {
                        onError("No se detectó voz. Pulsa el botón de voz nuevamente y habla después del tono.")
                    }
                }

                override fun onError(error: Int) {
                    if (expectedGen != sessionGeneration || sessionSettled) return
                    val elapsedMs = System.currentTimeMillis() - listenStartTimestamp
                    Log.w("SpeechService", "onError código=$error tras ${elapsedMs}ms (parcial='$lastPartialText', intento=$retryCount)")

                    // Si el usuario ya dijo palabras válidas en resultados parciales, rescatarlas
                    if (lastPartialText.isNotBlank() &&
                        (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    ) {
                        sessionSettled = true
                        val rescued = lastPartialText.trim()
                        destroyCurrentRecognizer()
                        onResult(rescued)
                        return
                    }

                    // Si el micrófono se cerró prematuramente por carrera del binder o servicio ocupado, reintentar sin cancelar
                    val isTransientBinderError =
                        elapsedMs < 1800L ||
                            error == SpeechRecognizer.ERROR_CLIENT ||
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                            error == SpeechRecognizer.ERROR_SERVER ||
                            error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ||
                            error == 11 // ERROR_SERVER_DISCONNECTED

                    if (isTransientBinderError && retryCount < 2) {
                        sessionSettled = true
                        retryCount++
                        destroyCurrentRecognizer()
                        mainHandler.postDelayed({
                            if (expectedGen == sessionGeneration) {
                                launchRecognizerSession(expectedGen, onReady, onResult, onError)
                            }
                        }, 350L)
                        return
                    }

                    sessionSettled = true
                    destroyCurrentRecognizer()
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO ->
                            "Hubo un problema con el micrófono. Pulsa el botón de voz nuevamente y habla después del tono."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Falta permiso de micrófono para escucharte."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Error de red al reconocer tu voz. Pulsa el botón de voz nuevamente y habla después del tono."
                        else ->
                            "No se detectó voz. Pulsa el botón de voz nuevamente y habla después del tono."
                    }
                    onError(errorMsg)
                }

                override fun onBeginningOfSpeech() {
                    Log.i("SpeechService", "onBeginningOfSpeech: El usuario comenzó a hablar.")
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.i("SpeechService", "onEndOfSpeech: El usuario terminó de hablar.")
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e("SpeechService", "Excepción al iniciar reconocimiento: ${e.message}", e)
            destroyCurrentRecognizer()
            onError("No se pudo activar el micrófono. Pulsa el botón de voz nuevamente y habla después del tono.")
        }
    }

    fun stopListening() {
        sessionGeneration++
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyCurrentRecognizer()
        } else {
            mainHandler.post { destroyCurrentRecognizer() }
        }
    }

    fun destroy() {
        stopListening()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
