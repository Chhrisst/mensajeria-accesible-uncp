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
    STANDBY,           // En reposo: Micrófono 100% apagado. Cero consumo de batería y cero parpadeo.
    ACTIVE_LISTENING,  // Micrófono abierto de forma controlada tras tocar la barra.
    PROCESSING,        // Consultando con Gemini AI.
    SPEAKING           // Narrando respuesta al usuario con síntesis de voz.
}

/**
 * Estado observable del Asistente de Voz.
 */
data class VoiceAssistantState(
    val mode: AssistantMode = AssistantMode.STANDBY,
    val lastRecognizedText: String = "",
    val feedbackMessage: String = "Toca para hablarle al Asistente IA"
)

/**
 * Gestor del Asistente de Voz Inteligente UNCP con Gemini.
 * El micrófono permanece apagado en STANDBY para no saturar el sistema ni parpadear en pantalla.
 * Se activa bajo demanda (Push-to-Talk / Gesto táctil), ejecuta la orden con IA,
 * responde por voz y libera el micrófono de inmediato.
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

    // Historial para conversaciones continuas con el Asistente IA
    private val dialogueHistory = mutableListOf<Pair<String, String>>()
    private var pendingAction: String? = null

    // Callbacks de navegación y UI
    var onOpenChat: ((Contact) -> Unit)? = null
    var onBackToContacts: (() -> Unit)? = null
    var onLogout: (() -> Unit)? = null
    var getCurrentScreen: () -> String = { "CONTACTS" }
    var getCurrentUser: () -> String = { "Usuario" }
    var getCurrentUserId: () -> String = { "" }
    var getActiveChatContact: () -> Contact? = { null }

    private var isDestroyed = false

    /**
     * Activación directa y controlada del Asistente.
     * Abre el micrófono una sola vez, sin bucles en segundo plano ni parpadeos continuos.
     */
    fun activateAssistant() {
        if (isDestroyed) return
        if (_state.value.mode == AssistantMode.PROCESSING || _state.value.mode == AssistantMode.SPEAKING) {
            ttsService.stop()
        }

        soundService.playMessageReceivedSound()
        vibrationService.vibrateStartListening()

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
                Log.w("VoiceAssistantManager", "Error de captura: $errorMsg")
                vibrationService.vibrateError()
                dialogueHistory.clear()
                pendingAction = null
                _state.update {
                    it.copy(
                        mode = AssistantMode.STANDBY,
                        feedbackMessage = "Asistente de Voz IA"
                    )
                }
            }
        )
    }

    fun triggerExplicitActivation() = activateAssistant()

    /**
     * Procesa la orden del usuario enviándola a Gemini.
     */
    private fun processCommandPhrase(rawText: String) {
        val cleanCommand = cleanWakePrefixes(rawText)
        if (cleanCommand.isBlank()) {
            stopAssistant()
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

            val result = GeminiDirectClient.processVoiceCommand(
                spokenText = cleanCommand,
                context = assistantContext,
                dialogueHistory = dialogueHistory
            )

            _state.update {
                it.copy(
                    mode = AssistantMode.SPEAKING,
                    feedbackMessage = result.spokenResponse
                )
            }

            // Ejecutar la mutación de estado o navegación
            executeAction(result)

            val isQuestion = result.spokenResponse.contains("?") ||
                    result.spokenResponse.contains("¿") ||
                    (result.action == "ADD_CONTACT" && (result.contactName.isNullOrBlank() && result.targetContact.isNullOrBlank())) ||
                    (result.action == "SEND_MESSAGE" && result.messageText.isNullOrBlank())

            // Narrar la respuesta por voz al usuario
            ttsService.speak(
                result.spokenResponse,
                onDone = {
                    if (isQuestion && !isDestroyed) {
                        // Guardar en el historial de la conversación el turno para recordar la pregunta
                        dialogueHistory.add(cleanCommand to result.spokenResponse)
                        if (dialogueHistory.size > 6) {
                            dialogueHistory.removeAt(0)
                        }
                        // Esperar 400ms a que el hardware libere el audio y reactivar el micrófono para la respuesta del usuario
                        mainHandler.postDelayed({
                            if (!isDestroyed) {
                                activateAssistant()
                            }
                        }, 400)
                    } else {
                        dialogueHistory.clear()
                        pendingAction = null
                        _state.update {
                            it.copy(
                                mode = AssistantMode.STANDBY,
                                feedbackMessage = "Asistente de Voz IA"
                            )
                        }
                    }
                }
            )
        }
    }

    /**
     * Limpia prefijos como "Ok asistente" si el usuario los incluyó al hablar.
     */
    private fun cleanWakePrefixes(text: String): String {
        return text
            .replace("(?i)ok asistente".toRegex(), "")
            .replace("(?i)okay asistente".toRegex(), "")
            .replace("(?i)okey asistente".toRegex(), "")
            .replace("(?i)oye asistente".toRegex(), "")
            .replace("(?i)asistente".toRegex(), "")
            .trim()
            .ifBlank { text.trim() }
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
                val identifier = result.contactName ?: result.targetContact ?: ""
                val clean = identifier.trim()
                val currentUserId = getCurrentUserId()
                if (clean.isNotBlank()) {
                    pendingAction = null
                    scope.launch {
                        if (currentUserId.isNotBlank()) {
                            val addResult = chatRepository.addContactByUsername(currentUserId, clean)
                            if (addResult.isSuccess) {
                                val added = addResult.getOrNull()!!
                                ttsService.speak("Contacto @${added.username} agregado exitosamente a tu lista.")
                                return@launch
                            }
                        }
                        val added = chatRepository.addContact(name = clean, phone = result.contactPhone ?: "")
                        ttsService.speak("Contacto ${added.name} guardado en tu lista.")
                    }
                } else {
                    // Cuando aún no hay nombre (la IA está preguntándolo), no crear ningún contacto falso
                    pendingAction = "ADD_CONTACT"
                }
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

    fun stopAssistant() {
        speechService.stopListening()
        ttsService.stop()
        dialogueHistory.clear()
        pendingAction = null
        _state.update {
            it.copy(
                mode = AssistantMode.STANDBY,
                feedbackMessage = "Asistente de Voz IA"
            )
        }
    }

    fun destroy() {
        isDestroyed = true
        stopAssistant()
    }
}
