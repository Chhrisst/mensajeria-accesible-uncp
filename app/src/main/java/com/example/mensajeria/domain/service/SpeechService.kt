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
 * Ejecuta de forma segura en el hilo principal y evita carreras asíncronas de destrucción.
 */
class SpeechService(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vibrationService = VibrationService.getInstance(context)

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private fun destroyCurrentRecognizer() {
        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w("SpeechService", "Aviso al detener reconocedor previo: ${e.message}")
        }
        speechRecognizer = null
    }

    fun startListening(
        onReady: () -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val executionBlock = Runnable {
            if (!isAvailable()) {
                Log.e("SpeechService", "SpeechRecognizer no está disponible en este dispositivo.")
                onError("El reconocimiento de voz no está disponible en este dispositivo.")
                return@Runnable
            }

            // Destrucción inmediata y síncrona de cualquier sesión previa
            destroyCurrentRecognizer()

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-PE")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicte su nombre o mensaje claramente")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.i("SpeechService", "onReadyForSpeech: Micrófono abierto y escuchando activamente.")
                        vibrationService.vibrateStartListening()
                        onReady()
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val recognized = matches?.firstOrNull()?.trim()
                        Log.i("SpeechService", "onResults detectado: $recognized")
                        if (!recognized.isNullOrEmpty()) {
                            onResult(recognized)
                        } else {
                            onError("No se detectó el texto dictado.")
                        }
                    }

                    override fun onError(error: Int) {
                        Log.e("SpeechService", "onError recibido código: $error")
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Error al capturar audio del micrófono."
                            SpeechRecognizer.ERROR_NO_MATCH -> "No se detectó ninguna palabra hablada."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tiempo de espera agotado. No se escuchó voz."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Error de conexión de red para reconocimiento."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono está ocupado. Intenta nuevamente."
                            11 /* ERROR_SERVER_DISCONNECTED */ -> "El servicio de voz se desconectó. Toca la pantalla para reintentar."
                            else -> "No se pudo completar el reconocimiento. Toca la pantalla para intentar de nuevo."
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

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("SpeechService", "Excepción al iniciar reconocimiento: ${e.message}", e)
                onError("Error al inicializar micrófono: ${e.localizedMessage}")
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            executionBlock.run()
        } else {
            mainHandler.post(executionBlock)
        }
    }

    fun stopListening() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyCurrentRecognizer()
        } else {
            mainHandler.post { destroyCurrentRecognizer() }
        }
    }

    fun destroy() {
        stopListening()
    }
}
