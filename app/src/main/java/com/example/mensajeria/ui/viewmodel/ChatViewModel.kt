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
import com.example.mensajeria.data.model.Message
import com.example.mensajeria.data.model.VoiceState
import com.example.mensajeria.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import com.example.mensajeria.domain.service.CallSignalParser
import com.example.mensajeria.domain.service.CallSignalType
import com.example.mensajeria.domain.service.DecryptedCallStreamItem
import com.example.mensajeria.domain.service.SmartReplyService
import com.example.mensajeria.domain.service.SpeechService
import com.example.mensajeria.domain.service.TextToSpeechService
import com.example.mensajeria.domain.service.VibrationService
import com.example.mensajeria.domain.service.VoiceCallListener
import com.example.mensajeria.domain.service.VoiceCallService
import com.example.mensajeria.domain.service.VoiceCallSignal
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.UUID

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

    val speechService = SpeechService(context)
    val vibrationService = VibrationService.getInstance(context)
    val soundService = com.example.mensajeria.domain.service.SoundNotificationService.getInstance(context)
    val audioService = com.example.mensajeria.domain.service.AudioRecorderPlayerService(context)
    val voiceCallService = VoiceCallService(context)
    var isDisabilityMode: Boolean = true
    var currentUserId: String = ""
    var currentUserName: String = "Usuario"
    private var pollingJob: Job? = null
    private var messagesJob: Job? = null
    private var refineJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var callTimerJob: Job? = null
    private var incomingCallPollingJob: Job? = null
    private var activeCallFallbackPollingJob: Job? = null

    @Volatile private var isOutgoingCaller: Boolean = false
    private val processedCallStreamIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val handledIncomingCallIds = Collections.synchronizedSet(mutableSetOf<String>())

    private companion object {
        const val TAG = "ChatViewModel"
        const val AI_TIMEOUT_MS = 12_000L
        const val CALL_RING_TIMEOUT_MS = 35_000L
    }

    val ttsService = TextToSpeechService(context) {
        // Inicializado correctamente
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        voiceCallService.listener = object : VoiceCallListener {
            override fun onIncomingInvite(callId: String, callerId: String, callerName: String) {
                handleIncomingCallInvite(callId, callerId, callerName)
            }

            override fun onRemoteRinging(callId: String, fromUserId: String) {
                if (_uiState.value.isInCall && _uiState.value.activeCallId == callId && !_uiState.value.isIncomingCall) {
                    _uiState.update { it.copy(callStatusText = "Timbrando...") }
                }
            }

            override fun onRemoteAccepted(callId: String, fromUserId: String) {
                if (_uiState.value.isInCall && _uiState.value.activeCallId == callId && !_uiState.value.isIncomingCall && _uiState.value.callStatusText != "En llamada") {
                    enterActiveConnectedCall()
                }
            }

            override fun onRemoteRejected(callId: String, fromUserId: String) {
                handleRemoteCallTerminated(callId, isRejected = true)
            }

            override fun onRemoteEnded(callId: String, fromUserId: String) {
                handleRemoteCallTerminated(callId, isRejected = false)
            }

            override fun onRemoteAudioChunk(callId: String, fromUserId: String, audioBase64: String) {
                if (_uiState.value.isInCall && _uiState.value.activeCallId == callId && !_uiState.value.isIncomingCall) {
                    voiceCallService.playRemotePcmBase64(audioBase64)
                }
            }
        }

        repository.onCallStreamMessagesLoaded = { contactId, items ->
            processCallStreamSignals(contactId, items)
        }

        repository.onNewIncomingMessageReceived = { senderContact, message ->
            handleIncomingMessageNotification(senderContact, message)
        }

        repository.onCallSignalDetectedFromContact = { contact, rawContent ->
            val parsed = CallSignalParser.parse(rawContent)
            if (parsed != null &&
                parsed.type == CallSignalType.INVITE &&
                !_uiState.value.isInCall &&
                parsed.callId !in handledIncomingCallIds &&
                (System.currentTimeMillis() - parsed.timestampMs) in -120_000L..90_000L
            ) {
                viewModelScope.launch {
                    val signals = repository.fetchCallStreamSignals(currentUserId, contact.id)
                    processCallStreamSignals(contact.id, signals)
                }
            }
        }

        // Mantener sincronizados el estado de conexión y los datos del contacto activo
        viewModelScope.launch {
            repository.contacts.collect { contactsList ->
                _uiState.update { state ->
                    val currentContactId = state.contact.id
                    val updatedContact = if (currentContactId.isNotBlank()) {
                        contactsList.find { it.id == currentContactId }
                    } else null
                    val partnerId = state.callPartner?.id
                    val updatedPartner = if (!partnerId.isNullOrBlank()) {
                        contactsList.find { it.id == partnerId }
                    } else null
                    var next = state
                    if (updatedContact != null && updatedContact != state.contact) {
                        next = next.copy(contact = updatedContact)
                    }
                    if (updatedPartner != null && updatedPartner != state.callPartner) {
                        next = next.copy(callPartner = updatedPartner)
                    }
                    next
                }
            }
        }

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
     * Vincula la sesión activa del usuario para escuchar llamadas entrantes por WebSocket y respaldo HTTP.
     */
    fun bindUserSession(userId: String, userName: String) {
        val cleanId = userId.trim()
        val cleanName = userName.trim().ifBlank { "Usuario" }
        if (cleanId.isBlank()) {
            currentUserId = ""
            currentUserName = "Usuario"
            incomingCallPollingJob?.cancel()
            incomingCallPollingJob = null
            voiceCallService.disconnectWebSocket()
            return
        }
        currentUserName = cleanName
        if (currentUserId == cleanId && incomingCallPollingJob?.isActive == true) {
            voiceCallService.connectWebSocket(cleanId)
            return
        }
        currentUserId = cleanId
        repository.setSessionUser(cleanId)
        voiceCallService.connectWebSocket(cleanId)
        startIncomingCallMonitor()
    }

    private fun handleIncomingMessageNotification(senderContact: Contact, message: Message) {
        viewModelScope.launch(Dispatchers.Main) {
            // 1. Tono agradable y nítido de mensaje nuevo
            soundService.playMessageReceivedSound()

            // 2. Vibración háptica de nuevo mensaje
            vibrationService.vibrateMessageReceived()

            // 3. Anuncio por voz del Asistente
            val isCurrentChatOpen = (_uiState.value.contact.id == senderContact.id)
            val announcement = if (message.isVoiceNote) {
                if (isCurrentChatOpen) {
                    "${senderContact.name} te envió una nota de voz."
                } else {
                    "Nuevo mensaje de voz de ${senderContact.name}."
                }
            } else {
                if (isCurrentChatOpen) {
                    "${senderContact.name}: ${message.text}"
                } else {
                    "Mensaje de ${senderContact.name}: ${message.text}"
                }
            }
            ttsService.speak(announcement)
        }
    }

    private fun startIncomingCallMonitor() {
        incomingCallPollingJob?.cancel()
        incomingCallPollingJob = viewModelScope.launch {
            var cycle = 0
            var lastPresenceSentMs = 0L
            while (currentUserId.isNotBlank()) {
                val uid = currentUserId
                val uname = currentUserName
                if (cycle == 0 || cycle % 3 == 0) {
                    repository.syncContactsFromRemote(uid)
                }
                val activeContacts = repository.contacts.value.filter { !it.id.startsWith("c_") }
                val nowMs = System.currentTimeMillis()
                if (activeContacts.isNotEmpty() && nowMs - lastPresenceSentMs >= 60_000L) {
                    lastPresenceSentMs = nowMs
                    broadcastPresenceSignal(CallSignalType.PRESENCE, uid, uname, activeContacts)
                }
                if (!_uiState.value.isInCall) {
                    for (c in activeContacts.take(16)) {
                        if (_uiState.value.isInCall || currentUserId != uid) break
                        val signals = repository.fetchCallStreamSignals(uid, c.id)
                        if (signals.isNotEmpty()) {
                            processCallStreamSignals(c.id, signals)
                        }
                    }
                }
                cycle++
                delay(1800L)
            }
        }
    }

    private suspend fun broadcastPresenceSignal(
        type: CallSignalType,
        userId: String,
        userName: String,
        contacts: List<Contact>
    ) {
        if (userId.isBlank() || contacts.isEmpty()) return
        val ts = System.currentTimeMillis()
        val encoded = CallSignalParser.encode(
            VoiceCallSignal(
                type = type,
                callId = "presence_${userId.take(8)}_$ts",
                callerId = userId,
                callerName = userName,
                timestampMs = ts
            )
        )
        for (c in contacts.take(8)) {
            if (c.id.startsWith("c_") || c.id.isBlank()) continue
            try {
                repository.sendMessageRemote(
                    userId = userId,
                    contactId = c.id,
                    text = encoded,
                    messageTypeOverride = "CALL_STREAM"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    /**
     * Notifica desconexión antes de cerrar sesión.
     */
    fun notifyUserOffline() {
        val uid = currentUserId
        val uname = currentUserName
        val snapshot = repository.contacts.value.filter { !it.id.startsWith("c_") }
        if (uid.isBlank() || snapshot.isEmpty()) return
        viewModelScope.launch {
            broadcastPresenceSignal(CallSignalType.OFFLINE, uid, uname, snapshot)
        }
    }

    private fun processCallStreamSignals(contactId: String, items: List<DecryptedCallStreamItem>) {
        if (items.isEmpty() || currentUserId.isBlank()) return
        val state = _uiState.value

        // 1. Si no estamos en llamada, buscar una invitación entrante activa para este usuario
        if (!state.isInCall) {
            val invite = CallSignalParser.findPendingIncomingInvite(
                items = items,
                currentUserId = currentUserId
            )
            if (invite != null && invite.callId !in handledIncomingCallIds) {
                val effectiveCallerId = invite.callerId.ifBlank { contactId }
                val knownName = repository.contacts.value.find { it.id == effectiveCallerId }?.name
                    ?: invite.callerName
                handleIncomingCallInvite(invite.callId, effectiveCallerId, knownName)
            }
            return
        }

        // 2. Si estamos en llamada con este contacto, procesar ACCEPT / REJECT / END / AUDIO
        val activeCallId = state.activeCallId
        val partnerId = state.callPartner?.id ?: state.contact.id
        if (activeCallId.isBlank() || (partnerId.isNotBlank() && partnerId != contactId)) return

        if (CallSignalParser.isCallRejected(items, activeCallId)) {
            handleRemoteCallTerminated(activeCallId, isRejected = true)
            return
        }
        if (CallSignalParser.hasCallTerminated(items, activeCallId)) {
            handleRemoteCallTerminated(activeCallId, isRejected = false)
            return
        }
        if (!state.isIncomingCall && state.callStatusText != "En llamada" &&
            CallSignalParser.isCallAccepted(items, activeCallId)
        ) {
            enterActiveConnectedCall()
            return
        }

        // 3. Reproducir bloques de voz remotos recibidos por CALL_STREAM si la llamada está conectada
        if (!state.isIncomingCall && state.callStatusText == "En llamada") {
            for (item in items) {
                if (item.isFromMe) continue
                if (!processedCallStreamIds.add(item.id)) continue
                val sig = CallSignalParser.parse(item.content) ?: continue
                if (sig.callId == activeCallId && sig.type == CallSignalType.AUDIO && item.audioBase64.isNotBlank()) {
                    voiceCallService.playRemotePcmBase64(item.audioBase64)
                }
            }
        }
    }

    private fun sendCallSignal(
        type: CallSignalType,
        callId: String,
        targetId: String,
        audioBase64: String = "",
        alsoSendHttpFallback: Boolean = true
    ) {
        if (currentUserId.isBlank() || targetId.isBlank() || callId.isBlank()) return
        val wsType = when (type) {
            CallSignalType.INVITE -> "CALL_INVITE"
            CallSignalType.RINGING -> "CALL_RINGING"
            CallSignalType.ACCEPT -> "CALL_ACCEPT"
            CallSignalType.REJECT -> "CALL_REJECT"
            CallSignalType.END -> "CALL_END"
            CallSignalType.AUDIO -> "CALL_AUDIO"
            CallSignalType.PRESENCE -> "CALL_PRESENCE"
            CallSignalType.OFFLINE -> "CALL_OFFLINE"
        }
        voiceCallService.sendWebSocketSignal(
            type = wsType,
            callId = callId,
            targetId = targetId,
            callerId = currentUserId,
            callerName = currentUserName,
            audioBase64 = audioBase64.takeIf { it.isNotBlank() }
        )

        if (alsoSendHttpFallback) {
            val encoded = CallSignalParser.encode(
                VoiceCallSignal(
                    type = type,
                    callId = callId,
                    callerId = currentUserId,
                    callerName = currentUserName,
                    timestampMs = System.currentTimeMillis()
                )
            )
            viewModelScope.launch {
                repository.sendMessageRemote(
                    userId = currentUserId,
                    contactId = targetId,
                    text = encoded,
                    audioBase64 = audioBase64,
                    messageTypeOverride = "CALL_STREAM"
                )
            }
        }
    }

    /**
     * Inicia una llamada de voz bidireccional en tiempo real mediante el servidor.
     */
    fun startVoiceCall() {
        val contact = _uiState.value.contact
        if (_uiState.value.isInCall || contact.id.isBlank()) return

        audioService.stopPlayback()
        speechService.stopListening()
        soundService.playCallRingbackTone()
        vibrationService.vibrateClick()

        val callId = UUID.randomUUID().toString()
        isOutgoingCaller = true
        processedCallStreamIds.clear()

        _uiState.update {
            it.copy(
                isInCall = true,
                isIncomingCall = false,
                activeCallId = callId,
                callPartner = contact,
                callStatusText = "Llamando a ${contact.name}...",
                callDurationSeconds = 0,
                isCallMuted = false,
                isSpeakerphoneOn = true
            )
        }
        if (isDisabilityMode) {
            ttsService.speak("Llamando a ${contact.name}.")
        }

        sendCallSignal(
            type = CallSignalType.INVITE,
            callId = callId,
            targetId = contact.id,
            alsoSendHttpFallback = true
        )

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            var lastRingtoneMs = startMs
            while (_uiState.value.isInCall &&
                _uiState.value.activeCallId == callId &&
                _uiState.value.callStatusText != "En llamada"
            ) {
                delay(800L)
                if (!_uiState.value.isInCall || _uiState.value.activeCallId != callId) return@launch
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastRingtoneMs >= 2600L) {
                    soundService.playCallRingbackTone()
                    lastRingtoneMs = nowMs
                }
                val signals = repository.fetchCallStreamSignals(currentUserId, contact.id)
                processCallStreamSignals(contact.id, signals)

                if (nowMs - startMs >= CALL_RING_TIMEOUT_MS && _uiState.value.callStatusText != "En llamada") {
                    if (isDisabilityMode) {
                        ttsService.speak("${contact.name} no contestó la llamada.")
                    }
                    endVoiceCall()
                    return@launch
                }
            }
        }
    }

    private fun handleIncomingCallInvite(callId: String, callerId: String, callerName: String) {
        if (callId.isBlank() || _uiState.value.isInCall) return
        if (!handledIncomingCallIds.add(callId)) return

        audioService.stopPlayback()
        speechService.stopListening()
        isOutgoingCaller = false
        processedCallStreamIds.clear()

        val partner = repository.contacts.value.find { it.id == callerId }
            ?: Contact(id = callerId, name = callerName.ifBlank { "Contacto" }, username = "")

        _uiState.update {
            it.copy(
                isInCall = true,
                isIncomingCall = true,
                activeCallId = callId,
                callPartner = partner,
                callStatusText = "Llamada entrante...",
                callDurationSeconds = 0,
                isCallMuted = false,
                isSpeakerphoneOn = true
            )
        }

        sendCallSignal(
            type = CallSignalType.RINGING,
            callId = callId,
            targetId = partner.id,
            alsoSendHttpFallback = false
        )

        if (isDisabilityMode) {
            ttsService.speak("Llamada entrante de ${partner.name}. Toca a la izquierda para contestar o a la derecha para rechazar.")
        }

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            while (_uiState.value.isInCall &&
                _uiState.value.isIncomingCall &&
                _uiState.value.activeCallId == callId
            ) {
                soundService.playIncomingCallRingtone()
                vibrationService.vibrateStartListening()
                delay(1800L)
                if (!_uiState.value.isInCall || !_uiState.value.isIncomingCall) return@launch
                val signals = repository.fetchCallStreamSignals(currentUserId, partner.id)
                processCallStreamSignals(partner.id, signals)
                if (System.currentTimeMillis() - startMs >= CALL_RING_TIMEOUT_MS) {
                    rejectIncomingCall()
                    return@launch
                }
            }
        }
    }

    fun acceptIncomingCall() {
        val state = _uiState.value
        if (!state.isInCall || !state.isIncomingCall) return
        val partnerId = state.callPartner?.id ?: state.contact.id
        val callId = state.activeCallId

        soundService.playButtonClickSound()
        vibrationService.vibrateSuccess()

        sendCallSignal(
            type = CallSignalType.ACCEPT,
            callId = callId,
            targetId = partnerId,
            alsoSendHttpFallback = true
        )
        enterActiveConnectedCall()
    }

    fun rejectIncomingCall() {
        val state = _uiState.value
        if (!state.isInCall) return
        val partnerId = state.callPartner?.id ?: state.contact.id
        val callId = state.activeCallId

        callTimerJob?.cancel()
        activeCallFallbackPollingJob?.cancel()
        voiceCallService.stopAudioStreaming()
        soundService.playButtonClickSound()
        vibrationService.vibrateClick()

        sendCallSignal(
            type = CallSignalType.REJECT,
            callId = callId,
            targetId = partnerId,
            alsoSendHttpFallback = true
        )

        _uiState.update {
            it.copy(
                isInCall = false,
                isIncomingCall = false,
                activeCallId = "",
                callPartner = null,
                callStatusText = "",
                callDurationSeconds = 0
            )
        }
        if (isDisabilityMode) {
            ttsService.speak("Llamada rechazada.")
        }
    }

    private fun enterActiveConnectedCall() {
        val state = _uiState.value
        val partner = state.callPartner ?: state.contact
        val callId = state.activeCallId

        callTimerJob?.cancel()
        activeCallFallbackPollingJob?.cancel()
        soundService.playSuccessSound()

        _uiState.update {
            it.copy(
                isInCall = true,
                isIncomingCall = false,
                callStatusText = "En llamada",
                callDurationSeconds = 0
            )
        }

        if (isDisabilityMode) {
            ttsService.speak("Llamada conectada con ${partner.name}.")
        }

        voiceCallService.setMuted(_uiState.value.isCallMuted)
        voiceCallService.startAudioStreaming(
            callId = callId,
            targetId = partner.id,
            callerId = currentUserId,
            callerName = currentUserName,
            speakerphoneOn = _uiState.value.isSpeakerphoneOn,
            onSendFallbackChunk = { b64Audio ->
                sendCallSignal(
                    type = CallSignalType.AUDIO,
                    callId = callId,
                    targetId = partner.id,
                    audioBase64 = b64Audio,
                    alsoSendHttpFallback = true
                )
            }
        )

        callTimerJob = viewModelScope.launch {
            while (_uiState.value.isInCall && _uiState.value.activeCallId == callId) {
                delay(1000L)
                _uiState.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1) }
            }
        }

        activeCallFallbackPollingJob = viewModelScope.launch {
            while (_uiState.value.isInCall &&
                !_uiState.value.isIncomingCall &&
                _uiState.value.activeCallId == callId
            ) {
                val interval = if (voiceCallService.isWebSocketConnected()) 2000L else 550L
                delay(interval)
                if (!_uiState.value.isInCall || _uiState.value.activeCallId != callId) break
                val signals = repository.fetchCallStreamSignals(currentUserId, partner.id)
                processCallStreamSignals(partner.id, signals)
            }
        }
    }

    private fun handleRemoteCallTerminated(callId: String, isRejected: Boolean) {
        val state = _uiState.value
        if (!state.isInCall || state.activeCallId != callId) return

        val partner = state.callPartner ?: state.contact
        val duration = state.callDurationSeconds
        val wasOutgoing = isOutgoingCaller
        isOutgoingCaller = false

        callTimerJob?.cancel()
        activeCallFallbackPollingJob?.cancel()
        voiceCallService.stopAudioStreaming()
        soundService.playButtonClickSound()
        vibrationService.vibrateClick()

        _uiState.update {
            it.copy(
                isInCall = false,
                isIncomingCall = false,
                activeCallId = "",
                callPartner = null,
                callStatusText = "",
                callDurationSeconds = 0
            )
        }

        if (wasOutgoing && duration > 0 && partner.id.isNotBlank()) {
            val summary = String.format(java.util.Locale.getDefault(), "Llamada de voz (%02d:%02d)", duration / 60, duration % 60)
            if (partner.id == _uiState.value.contact.id) {
                sendAndAnnounce(text = summary, successSpeech = "Llamada finalizada.", alwaysSpeak = false)
            } else {
                viewModelScope.launch {
                    repository.sendMessageRemote(currentUserId, partner.id, summary)
                }
            }
        }

        if (isDisabilityMode) {
            val msg = if (isRejected) "Llamada rechazada por ${partner.name}." else "Llamada finalizada."
            ttsService.speak(msg)
        }
    }

    fun toggleCallMute() {
        val next = !_uiState.value.isCallMuted
        _uiState.update { it.copy(isCallMuted = next) }
        voiceCallService.setMuted(next)
        if (isDisabilityMode) {
            ttsService.speak(if (next) "Micrófono silenciado." else "Micrófono activado.")
        }
    }

    fun toggleSpeakerphone() {
        val next = !_uiState.value.isSpeakerphoneOn
        _uiState.update { it.copy(isSpeakerphoneOn = next) }
        voiceCallService.setSpeakerphone(next)
        if (isDisabilityMode) {
            ttsService.speak(if (next) "Altavoz activado." else "Altavoz desactivado.")
        }
    }

    fun endVoiceCall() {
        val state = _uiState.value
        if (!state.isInCall) return
        if (state.isIncomingCall) {
            rejectIncomingCall()
            return
        }

        val partner = state.callPartner ?: state.contact
        val callId = state.activeCallId
        val duration = state.callDurationSeconds
        val wasOutgoing = isOutgoingCaller
        isOutgoingCaller = false

        callTimerJob?.cancel()
        activeCallFallbackPollingJob?.cancel()
        voiceCallService.stopAudioStreaming()
        soundService.playButtonClickSound()
        vibrationService.vibrateClick()

        sendCallSignal(
            type = CallSignalType.END,
            callId = callId,
            targetId = partner.id,
            alsoSendHttpFallback = true
        )

        _uiState.update {
            it.copy(
                isInCall = false,
                isIncomingCall = false,
                activeCallId = "",
                callPartner = null,
                callStatusText = "",
                callDurationSeconds = 0
            )
        }

        if (wasOutgoing && partner.id.isNotBlank()) {
            val mins = duration / 60
            val secs = duration % 60
            val summary = if (duration > 0) {
                String.format(java.util.Locale.getDefault(), "Llamada de voz (%02d:%02d)", mins, secs)
            } else {
                "Llamada de voz cancelada"
            }
            if (partner.id == _uiState.value.contact.id) {
                sendAndAnnounce(
                    text = summary,
                    successSpeech = "Llamada finalizada.",
                    alwaysSpeak = false
                )
            } else {
                viewModelScope.launch {
                    repository.sendMessageRemote(currentUserId, partner.id, summary)
                }
            }
        } else if (isDisabilityMode) {
            ttsService.speak("Llamada finalizada.")
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
        val sameContactActive = _uiState.value.contact.id == contact.id &&
            contact.id.isNotBlank() &&
            pollingJob?.isActive == true &&
            (userId.isBlank() || userId == currentUserId)
        if (sameContactActive) {
            return
        }

        refineJob?.cancel()
        val resolvedContact = repository.contacts.value.find { it.id == contact.id } ?: contact
        _uiState.update {
            it.copy(
                contact = resolvedContact,
                voiceState = VoiceState.IDLE,
                pendingSpokenText = "",
                smartReplies = emptyList()
            )
        }
        if (userId.isNotBlank()) {
            currentUserId = userId
        }
        if (isDisabilityMode) {
            ttsService.speak("Conversación abierta con ${resolvedContact.name}.")
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
                kotlinx.coroutines.delay(2000L)
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
        incomingCallPollingJob?.cancel()
        activeCallFallbackPollingJob?.cancel()
        voiceCallService.release()
        audioService.release()
        speechService.destroy()
        ttsService.shutdown()
    }
}
