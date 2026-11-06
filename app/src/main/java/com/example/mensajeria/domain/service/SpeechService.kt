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
 * Emite sonido de tono prompt y vibración en el momento exacto en que el micrófono se abre y está listo.
 */
class SpeechService(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val soundService = SoundNotificationService.getInstance(context)
    private val vibrationService = VibrationService.getInstance(context)

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        onReady: () -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            if (!isAvailable()) {
                Log.w("SpeechService", "SpeechRecognizer no está disponible en este dispositivo.")
                onError("El reconocimiento de voz no está disponible en este dispositivo.")
                return@post
            }

            stopListening()

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-PE")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicte su nombre o mensaje claramente")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("SpeechService", "Micrófono activado y listo para capturar voz.")
                        // Emitir tono auditivo (BEEP) y vibración táctil exactamente cuando el micrófono abre
                        soundService.playListeningPromptSound()
                        vibrationService.vibrateStartListening()
                        mainHandler.post { onReady() }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val recognized = matches?.firstOrNull()?.trim()
                        Log.d("SpeechService", "Resultado reconocido: $recognized")
                        mainHandler.post {
                            if (!recognized.isNullOrEmpty()) {
                                onResult(recognized)
                            } else {
                                onError("No se detectó el texto dictado.")
                            }
                        }
                    }

                    override fun onError(error: Int) {
                        Log.w("SpeechService", "Error de reconocimiento de voz código: $error")
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Error al capturar audio del micrófono."
                            SpeechRecognizer.ERROR_NO_MATCH -> "No se detectó ninguna palabra hablada."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tiempo de espera agotado. No se escuchó voz."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Error de conexión de red para reconocimiento."
                            else -> "No se pudo completar el reconocimiento. Código: $error"
                        }
                        mainHandler.post { onError(errorMsg) }
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d("SpeechService", "El usuario comenzó a hablar.")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d("SpeechService", "Fin de captura de voz.")
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
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }

    fun destroy() {
        stopListening()
    }
}
