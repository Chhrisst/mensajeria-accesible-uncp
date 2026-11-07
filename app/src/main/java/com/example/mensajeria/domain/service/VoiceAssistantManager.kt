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
 * Modos de interacción del Asistente de Voz Inteligente.
 */
enum class AssistantMode {
    STANDBY,           // En reposo visual (no animaciones, bar tranquilo)
    ACTIVE_LISTENING,  // Activado tras decir "Ok Asistente" o tras tocar la barra. Micrófono abierto para recibir la orden.
    PROCESSING,        // Consultando con Gemini AI
    SPEAKING           // Narrando respuesta al usuario
}

/**
 * Estado observable del Asistente de Voz.
 */
data class VoiceAssistantState(
    val mode: AssistantMode = AssistantMode.STANDBY,
    val isContinuousMode: Boolean = true,
    val lastRecognizedText: String = "",
    val feedbackMessage: String = "Di 'Ok, Asistente' o toca para hablar"
)

/**
 * Gestor del Asistente de Voz Autónomo e Interactivo de la UNCP.
 * Inicia siempre en modo STANDBY (en reposo) y solo se activa cuando:
 * 1. El usuario dice "Ok, Asistente"
 * 2. El usuario toca la tarjeta del Asistente
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
     * Inicia la escucha pasiva en segundo plano para detectar "Ok, Asistente".
     * Mantiene la interfaz visual en STANDBY (en reposo) sin alarmar al usuario.
     */
    fun startPassiveWakeWordListening() {
        if (isDestroyed) return
        if (_state.value.mode == AssistantMode.SPEAKING || _state.value.mode == AssistantMode.PROCESSING) return

        cancelScheduledRestart()
        isAwaitingCommand = false

        _state.update {
            it.copy(
                mode = AssistantMode.STANDBY,
                feedbackMessage = "Di 'Ok, Asistente' o toca para hablar"
            )
        }

        speechService.startListening(
            onReady = {
                // Micrófono abierto en segundo plano discretamente
            },
            onResult = { recognizedText ->
                _state.update { it.copy(lastRecognizedText = recognizedText) }
                handlePassiveResult(recognizedText)
            },
            onError = { errorMsg ->
                // Timeout normal de silencio en segundo plano: reiniciar suavemente
                if (_state.value.isContinuousMode && !isDestroyed && _state.value.mode == AssistantMode.STANDBY) {
                    scheduleListeningRestart(1200L)
                }
            }
        )
    }

    /**
     * Activación explícita mediante toque de pantalla en la barra del Asistente.
     */
    fun triggerExplicitActivation() {
        if (isDestroyed) return
        cancelScheduledRestart()

        soundService.playMessageReceivedSound()
        vibrationService.vibrateStartListening()

        isAwaitingCommand = true
        _state.update {
            it.copy(
                mode = AssistantMode.ACTIVE_LISTENING,
                feedbackMessage = "Te escucho... Di tu orden"
            )
        }

        speechService.startListening(
            onReady = {
                _state.update {
                    it.copy(
                        mode = AssistantMode.ACTIVE_LISTENING,
                        feedbackMessage = "Te escucho... Di tu orden"
                    )
                }
            },
            onResult = { recognizedText ->
                _state.update { it.copy(lastRecognizedText = recognizedText) }
                processCommandPhrase(recognizedText)
            },
            onError = { errorMsg ->
                isAwaitingCommand = false
                _state.update {
                    it.copy(
                        mode = AssistantMode.STANDBY,
                        feedbackMessage = "Di 'Ok, Asistente' o toca para hablar"
                    )
                }
                if (_state.value.isContinuousMode && !isDestroyed) {
                    scheduleListeningRestart(1000L)
                }
            }
        )
    }

    /**
     * Procesa lo capturado en modo pasivo, buscando estrictamente "Ok, Asistente".
     */
    private fun handlePassiveResult(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            if (_state.value.isContinuousMode && !isDestroyed) {
                scheduleListeningRestart(800L)
            }
            return
        }

        val (hasWakeWord, commandPart) = extractWakeWordAndCommand(cleanText)
        if (!hasWakeWord) {
            // Conversación ajena o ruido: ignorar completamente
            Log.d("VoiceAssistant", "Ignorando audio sin 'Ok, Asistente': $cleanText")
            if (_state.value.isContinuousMode && !isDestroyed) {
                scheduleListeningRestart(800L)
            }
            return
        }

        // ¡"Ok, Asistente" detectado!
        soundService.playMessageReceivedSound()
        vibrationService.vibrateSuccess()

        if (commandPart.isNotBlank()) {
            // El usuario dijo la orden completa de corrido (ej. "Ok asistente entra al chat con Ana")
            processCommandPhrase(commandPart)
        } else {
            // El usuario solo dijo "Ok Asistente" y esperó
            isAwaitingCommand = true
            _state.update {
                it.copy(
                    mode = AssistantMode.SPEAKING,
                    feedbackMessage = "¡Ok Asistente activado! Te escucho..."
                )
            }

            ttsService.speak(
                "¿Sí? Te escucho.",
                onDone = {
                    triggerExplicitActivation()
                }
            )
        }
    }

    /**
     * Procesa la orden directa del usuario enviándola a Gemini.
     */
    private fun processCommandPhrase(commandText: String) {
        isAwaitingCommand = false
        val cleanCommand = commandText.trim()
        if (cleanCommand.isBlank()) {
            startPassiveWakeWordListening()
            return
        }

        soundService.playButtonClickSound()
        vibrationService.vibrateSuccess()

        _state.update {
            it.copy(
                mode = AssistantMode.PROCESSING,
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

            val result = GeminiDirectClient.processVoiceCommand(cleanCommand, assistantContext)

            _state.update {
                it.copy(
                    mode = AssistantMode.SPEAKING,
                    feedbackMessage = result.spokenResponse
                )
            }

            // Ejecutar la mutación de estado o navegación
            executeAction(result)

            // Narrar la respuesta por voz al usuario
            ttsService.speak(
                result.spokenResponse,
                onDone = {
                    _state.update {
                        it.copy(
                            mode = AssistantMode.STANDBY,
                            feedbackMessage = "Di 'Ok, Asistente' o toca para hablar"
                        )
                    }
                    if (_state.value.isContinuousMode && !isDestroyed) {
                        scheduleListeningRestart(800L)
                    }
                }
            )
        }
    }

    /**
     * Detecta "Ok, Asistente" y sus variantes comunes de pronunciación en español.
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

        val wakePatterns = listOf(
            "ok asistente",
            "okay asistente",
            "okey asistente",
            "oye asistente",
            "hola asistente",
            "asistente"
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
            if (!isDestroyed && _state.value.mode == AssistantMode.STANDBY) {
                startPassiveWakeWordListening()
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
        _state.update {
            it.copy(
                mode = AssistantMode.STANDBY,
                feedbackMessage = "Di 'Ok, Asistente' o toca para hablar"
            )
        }
    }

    fun destroy() {
        isDestroyed = true
        cancelScheduledRestart()
        speechService.stopListening()
    }
}
