package com.example.mensajeria.domain.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.mensajeria.data.api.GeminiDirectClient
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.VoiceAssistantContext
import com.example.mensajeria.data.model.VoiceCommandResult
import com.example.mensajeria.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado observable del Asistente de Voz Inteligente impulsado por Gemini.
 */
data class VoiceAssistantState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val isSpeaking: Boolean = false,
    val isContinuousMode: Boolean = true,
    val lastRecognizedText: String = "",
    val feedbackMessage: String = "Di 'Ok, Asistente'"
)

/**
 * Gestor del Asistente de Voz Autónomo e Interactivo de la UNCP.
 * Requiere estrictamente la frase de activación "Ok, Asistente" para despertar en segundo plano,
 * o un toque táctil explícito, evitando activaciones accidentales por ruido ambiental.
 */
class VoiceAssistantManager(
    private val context: Context,
    private val chatRepository: ChatRepository,
    private val speechService: SpeechService,
    private val ttsService: TextToSpeechService,
    private val vibrationService: VibrationService,
    private val soundService: SoundNotificationService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(VoiceAssistantState())
    val state: StateFlow<VoiceAssistantState> = _state.asStateFlow()

    // Callbacks de navegación y UI
    var onOpenChat: ((Contact) -> Unit)? = null
    var onBackToContacts: (() -> Unit)? = null
    var onLogout: (() -> Unit)? = null
    var getCurrentScreen: () -> String = { "CONTACTS" }
    var getCurrentUser: () -> String = { "Usuario" }
    var getActiveChatContact: () -> Contact? = { null }

    private var isDestroyed = false
    private var isAwaitingCommand = false
    private var restartRunnable: Runnable? = null

    /**
     * Inicia o reinicia el ciclo de escucha para comandos de voz.
     */
    fun startListeningCycle(explicitPrompt: Boolean = false) {
        if (isDestroyed) return
        if (_state.value.isSpeaking || _state.value.isProcessing) return

        cancelScheduledRestart()

        if (explicitPrompt) {
            isAwaitingCommand = true
            soundService.playButtonClickSound()
            vibrationService.vibrateStartListening()
        }

        _state.update {
            it.copy(
                isListening = true,
                feedbackMessage = if (isAwaitingCommand) "Te escucho..." else "Di 'Ok, Asistente'..."
            )
        }

        speechService.startListening(
            onReady = {
                _state.update { it.copy(isListening = true) }
            },
            onResult = { recognizedText ->
                _state.update { it.copy(isListening = false, lastRecognizedText = recognizedText) }
                processSpokenText(recognizedText, isExplicit = explicitPrompt)
            },
            onError = { errorMsg ->
                _state.update { it.copy(isListening = false) }
                // Si estaba esperando la orden y hubo tiempo de espera, restablecer
                if (isAwaitingCommand) {
                    isAwaitingCommand = false
                    _state.update { it.copy(feedbackMessage = "Di 'Ok, Asistente'") }
                }

                // Si está en modo continuo y no está destruido, reiniciar suavemente
                if (_state.value.isContinuousMode && !isDestroyed) {
                    scheduleListeningRestart(1000L)
                }
            }
        )
    }

    /**
     * Detecta si la frase contiene la palabra clave estricta "Ok, Asistente".
     * Devuelve Pair(hasWakeWord, commandPart).
     */
    private fun extractWakeWordAndCommand(text: String): Pair<Boolean, String> {
        val lower = text.trim().lowercase()
        val normalized = lower
            .replace(",", " ")
            .replace(".", " ")
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace(Regex("\\s+"), " ")

        // Patrones estrictos de activación para "Ok, Asistente"
        val wakePatterns = listOf(
            "ok asistente",
            "okay asistente",
            "okey asistente",
            "oye asistente",
            "hola asistente"
        )

        for (pattern in wakePatterns) {
            val idx = normalized.indexOf(pattern)
            if (idx != -1) {
                val commandPart = lower.substring((idx + pattern.length).coerceAtMost(lower.length)).trim()
                return Pair(true, commandPart)
            }
        }
        return Pair(false, "")
    }

    /**
     * Procesa la frase hablada por el usuario con estricto control de activación.
     */
    private fun processSpokenText(text: String, isExplicit: Boolean) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            if (_state.value.isContinuousMode && !isDestroyed) {
                scheduleListeningRestart(700L)
            }
            return
        }

        // Caso 1: El asistente ya fue activado previamente y estaba esperando la orden
        if (isAwaitingCommand || isExplicit) {
            isAwaitingCommand = false
            executeCommandWithGemini(cleanText)
            return
        }

        // Caso 2: Escuchando pasivamente en segundo plano -> EXIGE estrictamente "Ok, Asistente"
        val (hasWakeWord, commandPart) = extractWakeWordAndCommand(cleanText)
        if (!hasWakeWord) {
            // Se ignora cualquier otra conversación o ruido ambiental
            Log.d("VoiceAssistant", "Audio ignorado por no incluir 'Ok, Asistente': $cleanText")
            if (_state.value.isContinuousMode && !isDestroyed) {
                scheduleListeningRestart(600L)
            }
            return
        }

        // ¡Palabra clave "Ok, Asistente" detectada!
        soundService.playMessageReceivedSound()
        vibrationService.vibrateSuccess()

        if (commandPart.isNotBlank()) {
            // El usuario dijo la orden en una sola frase: "Ok asistente, entra al chat con Ana"
            executeCommandWithGemini(commandPart)
        } else {
            // El usuario solo dijo "Ok, Asistente" y esperó respuesta
            isAwaitingCommand = true
            _state.update {
                it.copy(
                    isSpeaking = true,
                    feedbackMessage = "¡Ok Asistente activado! Te escucho..."
                )
            }
            ttsService.speak(
                "¿Sí? Te escucho.",
                onDone = {
                    _state.update {
                        it.copy(
                            isSpeaking = false,
                            feedbackMessage = "Dime tu orden..."
                        )
                    }
                    startListeningCycle(explicitPrompt = false)
                }
            )
        }
    }

    /**
     * Envía la orden del usuario a Gemini AI y ejecuta la acción correspondiente.
     */
    private fun executeCommandWithGemini(commandText: String) {
        soundService.playButtonClickSound()
        vibrationService.vibrateSuccess()

        _state.update {
            it.copy(
                isProcessing = true,
                feedbackMessage = "Consultando con Inteligencia Artificial..."
            )
        }

        scope.launch {
            val contactsList = chatRepository.contacts.value.map { it.name }
            val activeChat = getActiveChatContact()
            val recentMessages = chatRepository.messages.value.takeLast(4).map { "${it.senderName}: ${it.text}" }

            val assistantContext = VoiceAssistantContext(
                currentScreen = getCurrentScreen(),
                currentUserName = getCurrentUser(),
                contacts = contactsList,
                activeChatContact = activeChat?.name,
                recentMessages = recentMessages
            )

            val result = GeminiDirectClient.processVoiceCommand(commandText, assistantContext)

            _state.update {
                it.copy(
                    isProcessing = false,
                    isSpeaking = true,
                    feedbackMessage = result.spokenResponse
                )
            }

            // Ejecutar la acción resuelta por la IA
            executeAction(result)

            // Narrar la respuesta por voz al usuario ciego
            ttsService.speak(
                result.spokenResponse,
                onDone = {
                    _state.update {
                        it.copy(
                            isSpeaking = false,
                            feedbackMessage = "Di 'Ok, Asistente'"
                        )
                    }
                    if (_state.value.isContinuousMode && !isDestroyed) {
                        scheduleListeningRestart(600L)
                    }
                }
            )
        }
    }

    /**
     * Ejecuta las mutaciones de estado y navegación solicitadas por Gemini.
     */
    private fun executeAction(result: VoiceCommandResult) {
        when (result.action) {
            "OPEN_CHAT" -> {
                val target = result.targetContact
                if (!target.isNullOrBlank()) {
                    val found = chatRepository.contacts.value.firstOrNull {
                        it.name.contains(target, ignoreCase = true) || target.contains(it.name, ignoreCase = true)
                    }
                    if (found != null) {
                        onOpenChat?.invoke(found)
                    }
                }
            }

            "ADD_CONTACT" -> {
                val name = result.contactName ?: result.targetContact ?: "Nuevo Contacto"
                val phone = result.contactPhone ?: ""
                val added = chatRepository.addContact(name = name, phone = phone)
                Log.i("VoiceAssistantManager", "Contacto agregado: ${added.name}")
            }

            "DELETE_CONTACT" -> {
                val target = result.targetContact ?: result.contactName ?: ""
                if (target.isNotBlank()) {
                    val deleted = chatRepository.deleteContact(target)
                    Log.i("VoiceAssistantManager", "Contacto eliminado: ${deleted?.name}")
                }
            }

            "MODIFY_CONTACT" -> {
                val target = result.targetContact ?: ""
                val newName = result.contactName ?: ""
                if (target.isNotBlank() && newName.isNotBlank()) {
                    chatRepository.updateContact(target, newName)
                }
            }

            "SEND_MESSAGE" -> {
                val text = result.messageText
                if (!text.isNullOrBlank()) {
                    val target = result.targetContact
                    if (target != null) {
                        val contact = chatRepository.contacts.value.firstOrNull {
                            it.name.contains(target, ignoreCase = true) || target.contains(it.name, ignoreCase = true)
                        }
                        if (contact != null) {
                            onOpenChat?.invoke(contact)
                        }
                    }
                    chatRepository.sendMessage(text)
                }
            }

            "BACK" -> {
                onBackToContacts?.invoke()
            }

            "LOGOUT" -> {
                onLogout?.invoke()
            }
        }
    }

    private fun scheduleListeningRestart(delayMs: Long) {
        cancelScheduledRestart()
        restartRunnable = Runnable {
            if (!isDestroyed && !_state.value.isSpeaking && !_state.value.isProcessing) {
                startListeningCycle(explicitPrompt = false)
            }
        }
        mainHandler.postDelayed(restartRunnable!!, delayMs)
    }

    private fun cancelScheduledRestart() {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = null
    }

    fun stopAssistant() {
        cancelScheduledRestart()
        isAwaitingCommand = false
        speechService.stopListening()
        _state.update { it.copy(isListening = false, isProcessing = false) }
    }

    fun setContinuousMode(enabled: Boolean) {
        _state.update { it.copy(isContinuousMode = enabled) }
        if (!enabled) {
            stopAssistant()
        } else {
            startListeningCycle(explicitPrompt = false)
        }
    }

    fun destroy() {
        isDestroyed = true
        cancelScheduledRestart()
        speechService.stopListening()
    }
}
