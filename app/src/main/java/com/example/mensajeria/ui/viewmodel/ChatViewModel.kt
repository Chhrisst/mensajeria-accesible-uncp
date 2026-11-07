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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel principal que orquesta la arquitectura MVVM con Unidirectional Data Flow (UDF).
 *
 * Administra el estado de la conversación, el flujo de confirmación auditiva,
 * las sugerencias de Inteligencia Artificial (Smart Replies) y la retroalimentación multimodal.
 */
class ChatViewModel(
    context: Context,
    private val repository: ChatRepository = ChatRepository(),
    private val smartReplyService: SmartReplyService = SmartReplyService()
) : ViewModel() {

    private val speechService = SpeechService(context)
    private val vibrationService = VibrationService(context)
    val soundService = com.example.mensajeria.domain.service.SoundNotificationService(context)
    var isDisabilityMode: Boolean = true
    var currentUserId: String = ""
    private var pollingJob: kotlinx.coroutines.Job? = null

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
    }

    private fun speakWelcome() {
        val welcome = "Bienvenido al chat accesible de la UNCP. Mantén presionado el botón inferior para dictar, o pulsa las respuestas sugeridas por Inteligencia Artificial."
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

                // Perfeccionamiento y puntuación inteligente con Gemini
                viewModelScope.launch {
                    val refined = try {
                        val response = RetrofitClient.api.refineSpeech(ApiAiRefineSpeechRequest(rawText = recognizedText))
                        if (response.isSuccessful && !response.body()?.result.isNullOrBlank()) {
                            response.body()!!.result
                        } else {
                            GeminiDirectClient.refineSpeech(recognizedText)
                        }
                    } catch (_: Exception) {
                        GeminiDirectClient.refineSpeech(recognizedText)
                    }

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
            val contactId = _uiState.value.contact.id
            if (currentUserId.isNotBlank() && contactId.isNotBlank()) {
                viewModelScope.launch {
                    repository.sendMessageRemote(currentUserId, contactId, textToSend)
                }
            } else {
                repository.sendMessage(textToSend)
            }

            _uiState.update {
                it.copy(
                    voiceState = VoiceState.IDLE,
                    pendingSpokenText = "",
                    feedbackMessage = "Mensaje enviado con éxito."
                )
            }
            ttsService.speak("Mensaje enviado: $textToSend")
        }
    }

    /**
     * Cancela el mensaje pendiente para volver a dictar.
     */
    fun cancelPendingMessage() {
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
        val contactId = _uiState.value.contact.id
        if (currentUserId.isNotBlank() && contactId.isNotBlank()) {
            viewModelScope.launch {
                repository.sendMessageRemote(currentUserId, contactId, reply)
            }
        } else {
            repository.sendMessage(reply)
        }

        _uiState.update {
            it.copy(
                voiceState = VoiceState.IDLE,
                pendingSpokenText = "",
                feedbackMessage = "Respuesta rápida enviada: $reply"
            )
        }
        ttsService.speak("Respuesta rápida enviada: $reply")
    }

    /**
     * Simula la recepción de un nuevo mensaje con sonido agradable de notificación.
     */
    fun simulateIncomingMessage(text: String) {
        soundService.playMessageReceivedSound()
        vibrationService.vibrateSuccess()
        repository.receiveMessage(text, _uiState.value.contact.name)
        val replies = smartReplyService.generateReplies(text)
        _uiState.update { it.copy(smartReplies = replies) }

        if (isDisabilityMode) {
            val announce = "Nuevo mensaje de ${_uiState.value.contact.name}: $text."
            ttsService.speak(announce)
        }
    }

    /**
     * Enviar mensaje de texto común (modo estándar WhatsApp).
     */
    fun sendTextMessage(text: String) {
        if (text.isNotBlank()) {
            vibrationService.vibrateSuccess()
            val contactId = _uiState.value.contact.id
            if (currentUserId.isNotBlank() && contactId.isNotBlank()) {
                viewModelScope.launch {
                    repository.sendMessageRemote(currentUserId, contactId, text.trim())
                }
            } else {
                repository.sendMessage(text.trim())
            }
            if (isDisabilityMode) {
                ttsService.speak("Mensaje enviado: ${text.trim()}")
            }
        }
    }

    /**
     * Enviar mensaje como Nota de Voz grabada.
     */
    fun sendVoiceNote(durationSeconds: Int = 4) {
        vibrationService.vibrateSuccess()
        val contactId = _uiState.value.contact.id
        val content = "Nota de voz grabada (${durationSeconds}s)"
        if (currentUserId.isNotBlank() && contactId.isNotBlank()) {
            viewModelScope.launch {
                repository.sendMessageRemote(
                    currentUserId,
                    contactId,
                    content,
                    isVoiceNote = true,
                    voiceDurationSeconds = durationSeconds
                )
            }
        } else {
            repository.sendMessage(content, isVoiceNote = true, voiceDurationSeconds = durationSeconds)
        }
        if (isDisabilityMode) {
            ttsService.speak("Nota de voz de $durationSeconds segundos enviada.")
        }
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
            val summary = try {
                val response = RetrofitClient.api.summarizeMessage(ApiAiSummarizeRequest(text = lastMsg.text))
                if (response.isSuccessful && !response.body()?.result.isNullOrBlank()) {
                    response.body()!!.result
                } else {
                    GeminiDirectClient.summarize(lastMsg.text)
                }
            } catch (_: Exception) {
                GeminiDirectClient.summarize(lastMsg.text)
            }

            soundService.playSuccessSound()
            vibrationService.vibrateSuccess()
            ttsService.speak("Resumen: $summary")
        }
    }

    fun setContact(contact: Contact, userId: String = "") {
        _uiState.update { it.copy(contact = contact) }
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
            viewModelScope.launch {
                repository.loadMessagesFromRemote(currentUserId, contactId)
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
                    repository.loadMessagesFromRemote(currentUserId, contactId)
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        speechService.destroy()
        ttsService.shutdown()
    }
}
