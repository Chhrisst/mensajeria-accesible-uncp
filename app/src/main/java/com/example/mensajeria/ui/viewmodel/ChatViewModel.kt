package com.example.mensajeria.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mensajeria.data.api.ApiAiRefineSpeechRequest
import com.example.mensajeria.data.api.ApiAiSummarizeRequest
import com.example.mensajeria.data.api.GeminiDirectClient
import com.example.mensajeria.data.api.RetrofitClient
import com.example.mensajeria.data.model.ChatUiState
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.VoiceState
import com.example.mensajeria.data.repository.ChatRepository
import com.example.mensajeria.domain.service.SmartReplyService
import com.example.mensajeria.domain.service.SpeechService
import com.example.mensajeria.domain.service.TextToSpeechService
import com.example.mensajeria.domain.service.VibrationService
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel principal que orquesta la arquitectura MVVM con Unidirectional Data Flow (UDF).
 *
 * Administra el estado de la conversación, el flujo de confirmación auditiva,
 * las sugerencias de Inteligencia Artificial (Smart Replies) y la retroalimentación multimodal.
 */
class ChatViewModel(
    context: Context,
    // Público para que MainActivity reutilice la misma instancia tras recrearse (rotación, cambio de fuente, etc.)
    val repository: ChatRepository = ChatRepository(),
    private val smartReplyService: SmartReplyService = SmartReplyService()
) : ViewModel() {

    private val speechService = SpeechService(context)
    private val vibrationService = VibrationService.getInstance(context)
    val soundService = com.example.mensajeria.domain.service.SoundNotificationService.getInstance(context)
    val audioService = com.example.mensajeria.domain.service.AudioRecorderPlayerService(context)
    var isDisabilityMode: Boolean = true
    var currentUserId: String = ""
    private var pollingJob: Job? = null
    private var messagesJob: Job? = null
    private var refineJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var callTimerJob: Job? = null

    private companion object {
        const val TAG = "ChatViewModel"
        const val AI_TIMEOUT_MS = 12_000L
    }

    val ttsService = TextToSpeechService(context) {
        // Inicializado correctamente
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Observar flujo de mensajes desde el repositorio
        viewModelScope.launch {
            repository.messages.collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
                // Actualizar sugerencias inteligentes basadas en el último mensaje recibido
                val lastReceived = msgs.lastOrNull { !it.isOutgoing }
                if (lastReceived != null) {
                    val replies = smartReplyService.generateReplies(lastReceived.text)
                    _uiState.update { it.copy(smartReplies = replies) }
                }
            }
        }

        viewModelScope.launch {
            audioService.playingMessageId.collect { msgId ->
                _uiState.update { it.copy(playingMessageId = msgId) }
            }
        }

        viewModelScope.launch {
            audioService.playbackProgress.collect { prog ->
                _uiState.update { it.copy(playbackProgress = prog) }
            }
        }
    }

    private fun speakWelcome() {
        val welcome = "Bienvenido a la aplicación de mensajería. Mantén presionado el botón inferior para dictar, o pulsa las respuestas sugeridas por Inteligencia Artificial."
        ttsService.speak(welcome)
    }

    /**
     * Inicia el proceso de captura por voz.
     */
    fun startListening() {
        vibrationService.vibrateStartListening()
        ttsService.stop()

        _uiState.update {
            it.copy(
                voiceState = VoiceState.LISTENING,
                feedbackMessage = "Escuchando... Dicte su mensaje ahora."
            )
        }

        speechService.startListening(
            onReady = {
                // Micrófono listo
            },
            onResult = { recognizedText ->
                vibrationService.vibrateSuccess()
                _uiState.update {
                    it.copy(
                        voiceState = VoiceState.CONFIRMING,
                        pendingSpokenText = recognizedText,
                        feedbackMessage = "Optimizando con IA..."
                    )
                }
                ttsService.speak("Optimizando texto...")

                // Perfeccionamiento y puntuación inteligente con Gemini.
                // Con tope de tiempo: si la IA no responde, se usa el texto tal cual se dictó en lugar de
                // dejar la pantalla en "Optimizando con IA..." indefinidamente.
                refineJob?.cancel()
                refineJob = viewModelScope.launch {
                    val rawRefined = requestAiText(
                        backendCall = { RetrofitClient.api.refineSpeech(ApiAiRefineSpeechRequest(rawText = recognizedText)).let { r -> if (r.isSuccessful) r.body()?.result else null } },
                        geminiFallback = { GeminiDirectClient.refineSpeech(recognizedText) }
                    ) ?: recognizedText
                    val refined = com.example.mensajeria.domain.service.IntonationPunctuationPolisher.polish(rawRefined)

                    // Si el usuario canceló mientras se optimizaba, no reabrir la confirmación
                    if (_uiState.value.voiceState != VoiceState.CONFIRMING) return@launch

                    _uiState.update {
                        it.copy(
                            voiceState = VoiceState.CONFIRMING,
                            pendingSpokenText = refined,
                            feedbackMessage = "Dijiste: \"$refined\""
                        )
                    }
                    vibrationService.vibrateSuccess()
                    ttsService.speak("Dijiste: $refined. Pulsa ambos lados para enviar o la izquierda para cancelar.")
                }
            },
            onError = { errorMsg ->
                vibrationService.vibrateError()
                _uiState.update {
                    it.copy(
                        voiceState = VoiceState.ERROR,
                        pendingSpokenText = "",
                        feedbackMessage = errorMsg
                    )
                }
                ttsService.speak(errorMsg)
            }
        )
    }

    /**
     * Cancela la captura de voz actual.
     */
    fun stopListening() {
        speechService.stopListening()
        refineJob?.cancel()
        if (_uiState.value.voiceState == VoiceState.LISTENING) {
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.IDLE,
                    feedbackMessage = "Dictado detenido."
                )
            }
        }
    }

    /**
     * Confirma el mensaje dictado y lo envía.
     */
    fun confirmAndSendMessage() {
        val textToSend = _uiState.value.pendingSpokenText
        if (textToSend.isNotBlank()) {
            vibrationService.vibrateSuccess()
            refineJob?.cancel()
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.IDLE,
                    pendingSpokenText = "",
                    feedbackMessage = "Enviando mensaje..."
                )
            }
            sendAndAnnounce(textToSend, successSpeech = "Mensaje enviado: $textToSend", alwaysSpeak = true)
        }
    }

    /**
     * Envía al servidor y anuncia el resultado REAL. Antes se anunciaba "Mensaje enviado" antes de
     * conocer la respuesta, aunque no hubiera conexión.
     */
    private fun sendAndAnnounce(
        text: String,
        successSpeech: String,
        alwaysSpeak: Boolean,
        isVoiceNote: Boolean = false,
        voiceDurationSeconds: Int = 0,
        audioBase64: String = ""
    ) {
        val contactId = _uiState.value.contact.id
        if (currentUserId.isBlank() || contactId.isBlank()) {
            repository.sendMessage(
                text,
                isVoiceNote = isVoiceNote,
                voiceDurationSeconds = voiceDurationSeconds,
                audioBase64 = audioBase64
            )
            if (alwaysSpeak || isDisabilityMode) ttsService.speak(successSpeech)
            return
        }
        viewModelScope.launch {
            val result = repository.sendMessageRemote(
                currentUserId,
                contactId,
                text,
                isVoiceNote = isVoiceNote,
                voiceDurationSeconds = voiceDurationSeconds,
                audioBase64 = audioBase64
            )
            if (result.isSuccess) {
                _uiState.update { it.copy(feedbackMessage = "Mensaje enviado con éxito.") }
                if (alwaysSpeak || isDisabilityMode) ttsService.speak(successSpeech)
            } else {
                vibrationService.vibrateError()
                val msg = "No se pudo enviar el mensaje. Revisa tu conexión a internet. Quedó guardado en el teléfono."
                _uiState.update { it.copy(feedbackMessage = msg) }
                // El fallo se anuncia siempre: el usuario debe saber que su mensaje no llegó.
                ttsService.speak(msg)
            }
        }
    }

    /**
     * Cancela el mensaje pendiente para volver a dictar.
     */
    fun cancelPendingMessage() {
        refineJob?.cancel()
        vibrationService.vibrateError()
        _uiState.update {
            it.copy(
                voiceState = VoiceState.IDLE,
                pendingSpokenText = "",
                feedbackMessage = "Mensaje cancelado. Puede dictar de nuevo."
            )
        }
        ttsService.speak("Mensaje cancelado.")
    }

    /**
     * Envía inmediatamente una sugerencia de Inteligencia Artificial (Smart Reply).
     * Reduce significativamente el tiempo de respuesta comunicativa.
     */
    fun selectSmartReply(reply: String) {
        vibrationService.vibrateSuccess()
        refineJob?.cancel()
        _uiState.update {
            it.copy(
                voiceState = VoiceState.IDLE,
                pendingSpokenText = "",
                feedbackMessage = "Enviando respuesta rápida..."
            )
        }
        sendAndAnnounce(reply, successSpeech = "Respuesta rápida enviada: $reply", alwaysSpeak = true)
    }

    /**
     * Enviar mensaje de texto común (modo estándar WhatsApp).
     */
    fun sendTextMessage(text: String) {
        if (text.isNotBlank()) {
            vibrationService.vibrateSuccess()
            val clean = text.trim()
            sendAndAnnounce(clean, successSpeech = "Mensaje enviado: $clean", alwaysSpeak = false)
        }
    }

    /**
     * Inicia la grabación real de una nota de voz con cronómetro estilo WhatsApp.
     */
    fun startVoiceNoteRecording() {
        if (_uiState.value.isRecordingVoiceNote) return
        ttsService.stop()
        soundService.playButtonClickSound()
        vibrationService.vibrateStartListening()
        audioService.startRecording()

        _uiState.update {
            it.copy(isRecordingVoiceNote = true, recordingSeconds = 0)
        }

        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (_uiState.value.isRecordingVoiceNote) {
                kotlinx.coroutines.delay(1000L)
                _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
            }
        }
    }

    /**
     * Finaliza la grabación actual de la nota de voz y la envía cifrada con su audio real.
     */
    fun finishAndSendVoiceNoteRecording() {
        if (!_uiState.value.isRecordingVoiceNote) return
        recordingTimerJob?.cancel()
        val recorded = audioService.stopRecordingAndEncode()
        _uiState.update { it.copy(isRecordingVoiceNote = false, recordingSeconds = 0) }
        if (recorded != null) {
            sendVoiceNote(durationSeconds = recorded.durationSeconds, audioBase64 = recorded.base64Audio)
        }
    }

    /**
     * Cancela y descarta la grabación de nota de voz en curso.
     */
    fun cancelVoiceNoteRecording() {
        recordingTimerJob?.cancel()
        audioService.cancelRecording()
        vibrationService.vibrateClick()
        _uiState.update { it.copy(isRecordingVoiceNote = false, recordingSeconds = 0) }
    }

    /**
     * Enviar mensaje como Nota de Voz grabada (con audio real Base64).
     */
    fun sendVoiceNote(durationSeconds: Int = 4, audioBase64: String = "") {
        vibrationService.vibrateSuccess()
        val content = "Nota de voz (${durationSeconds}s)"
        sendAndAnnounce(
            text = content,
            successSpeech = "Nota de voz de $durationSeconds segundos enviada.",
            alwaysSpeak = false,
            isVoiceNote = true,
            voiceDurationSeconds = durationSeconds,
            audioBase64 = audioBase64
        )
    }

    /**
     * Reproduce o detiene el audio real de una nota de voz (para emisor y receptor).
     */
    fun togglePlayVoiceNote(message: com.example.mensajeria.data.model.Message) {
        audioService.togglePlay(
            messageId = message.id,
            audioBase64 = message.audioBase64,
            fallbackText = message.text,
            onPlayFallbackTts = { fallback ->
                ttsService.speak(fallback)
            }
        )
    }

    /**
     * Inicia una llamada de voz en tiempo real mediante el servidor.
     */
    fun startVoiceCall() {
        val contact = _uiState.value.contact
        if (_uiState.value.isInCall) return
        audioService.stopPlayback()
        soundService.playCallRingbackTone()
        vibrationService.vibrateClick()

        _uiState.update {
            it.copy(
                isInCall = true,
                callStatusText = "Llamando a ${contact.name}...",
                callDurationSeconds = 0,
                isCallMuted = false,
                isSpeakerphoneOn = true
            )
        }
        if (isDisabilityMode) {
            ttsService.speak("Llamando a ${contact.name}.")
        }

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            // Señalización inicial hacia el servidor
            if (currentUserId.isNotBlank() && contact.id.isNotBlank()) {
                repository.sendMessageRemote(
                    userId = currentUserId,
                    contactId = contact.id,
                    text = "CALL_INVITE:${System.currentTimeMillis()}",
                    messageTypeOverride = "CALL_STREAM"
                )
            }
            kotlinx.coroutines.delay(1400L)
            if (!_uiState.value.isInCall) return@launch
            soundService.playCallRingbackTone()
            kotlinx.coroutines.delay(1400L)
            if (!_uiState.value.isInCall) return@launch

            soundService.playSuccessSound()
            _uiState.update { it.copy(callStatusText = "En llamada") }
            if (isDisabilityMode) {
                ttsService.speak("Llamada conectada con ${contact.name}.")
            }
            while (_uiState.value.isInCall) {
                kotlinx.coroutines.delay(1000L)
                _uiState.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1) }
            }
        }
    }

    fun toggleCallMute() {
        val next = !_uiState.value.isCallMuted
        _uiState.update { it.copy(isCallMuted = next) }
        if (isDisabilityMode) {
            ttsService.speak(if (next) "Micrófono silenciado." else "Micrófono activado.")
        }
    }

    fun toggleSpeakerphone() {
        val next = !_uiState.value.isSpeakerphoneOn
        _uiState.update { it.copy(isSpeakerphoneOn = next) }
        if (isDisabilityMode) {
            ttsService.speak(if (next) "Altavoz activado." else "Altavoz desactivado.")
        }
    }

    fun endVoiceCall() {
        if (!_uiState.value.isInCall) return
        callTimerJob?.cancel()
        val duration = _uiState.value.callDurationSeconds
        soundService.playButtonClickSound()
        vibrationService.vibrateClick()

        _uiState.update {
            it.copy(isInCall = false, callStatusText = "", callDurationSeconds = 0)
        }
        val mins = duration / 60
        val secs = duration % 60
        val summary = if (duration > 0) {
            String.format(java.util.Locale.getDefault(), "Llamada de voz (%02d:%02d)", mins, secs)
        } else {
            "Llamada de voz cancelada"
        }
        sendAndAnnounce(
            text = summary,
            successSpeech = "Llamada finalizada.",
            alwaysSpeak = false
        )
    }

    /**
     * Lee en voz alta toda la conversación o el último mensaje.
     */
    fun repeatLastMessage() {
        val lastMsg = _uiState.value.messages.lastOrNull()
        if (lastMsg != null) {
            val prefix = if (lastMsg.isOutgoing) "Tu último mensaje enviado fue" else "El último mensaje recibido fue"
            ttsService.speak("$prefix: ${lastMsg.text}")
        } else {
            ttsService.speak("Aún no hay mensajes en esta conversación.")
        }
    }

    /**
     * Resume con IA (Google Gemini) el mensaje relevante más reciente de la conversación.
     */
    fun summarizeLastMessage() {
        val lastMsg = _uiState.value.messages.lastOrNull { !it.isOutgoing }
            ?: _uiState.value.messages.lastOrNull()

        if (lastMsg == null) {
            ttsService.speak("No hay mensajes para resumir en esta conversación.")
            return
        }

        vibrationService.vibrateHover()
        ttsService.speak("Generando resumen con Inteligencia Artificial...")

        viewModelScope.launch {
            val summary = requestAiText(
                backendCall = { RetrofitClient.api.summarizeMessage(ApiAiSummarizeRequest(text = lastMsg.text)).let { r -> if (r.isSuccessful) r.body()?.result else null } },
                geminiFallback = { GeminiDirectClient.summarize(lastMsg.text) }
            )
            if (summary == null) {
                vibrationService.vibrateError()
                ttsService.speak("No se pudo generar el resumen. Revisa tu conexión a internet e inténtalo de nuevo.")
                return@launch
            }

            soundService.playSuccessSound()
            vibrationService.vibrateSuccess()
            ttsService.speak("Resumen: $summary")
        }
    }

    fun setContact(contact: Contact, userId: String = "") {
        refineJob?.cancel()
        _uiState.update {
            it.copy(
                contact = contact,
                voiceState = VoiceState.IDLE,
                pendingSpokenText = "",
                smartReplies = emptyList()
            )
        }
        if (userId.isNotBlank()) {
            currentUserId = userId
        }
        if (isDisabilityMode) {
            ttsService.speak("Conversación abierta con ${contact.name}.")
        }

        // Cargar mensajes de la base de datos
        loadMessages()

        // Iniciar refresco en segundo plano
        startMessagesPolling()
    }

    fun loadMessages() {
        val contactId = _uiState.value.contact.id
        if (currentUserId.isNotBlank() && contactId.isNotBlank()) {
            // Cancelar una carga anterior: si respondía tarde, podía pisar la conversación recién abierta
            messagesJob?.cancel()
            messagesJob = viewModelScope.launch {
                repository.loadMessagesFromRemote(currentUserId, contactId, loadLocalFirst = true)
            }
        }
    }

    private fun startMessagesPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(4000)
                val contactId = _uiState.value.contact.id
                if (currentUserId.isNotBlank() && contactId.isNotBlank()) {
                    repository.loadMessagesFromRemote(currentUserId, contactId, loadLocalFirst = false)
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        messagesJob?.cancel()
        messagesJob = null
    }

    /**
     * Pide texto a la IA: primero al backend, luego directo a Gemini, con un tope de tiempo total.
     * Devuelve null si nada respondió a tiempo (el llamador decide el texto de respaldo).
     */
    private suspend fun requestAiText(
        backendCall: suspend () -> String?,
        geminiFallback: suspend () -> String?
    ): String? = withTimeoutOrNull(AI_TIMEOUT_MS) {
        val fromBackend = try {
            backendCall()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Backend de IA no disponible: ${e.message}")
            null
        }
        fromBackend?.takeIf { it.isNotBlank() } ?: try {
            geminiFallback()?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini no disponible: ${e.message}")
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        recordingTimerJob?.cancel()
        callTimerJob?.cancel()
        audioService.release()
        speechService.destroy()
        ttsService.shutdown()
    }
}
