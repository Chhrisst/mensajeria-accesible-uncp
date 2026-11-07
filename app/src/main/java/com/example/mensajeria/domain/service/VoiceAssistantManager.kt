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
    val feedbackMessage: String = "Di 'Ok Asistente' o toca la barra inferior"
)

/**
 * Gestor del Asistente de Voz Autónomo e Interactivo de la UNCP.
 * Orquesta reconocimiento de voz, comprensión del lenguaje natural (NLU con Gemini),
 * síntesis de voz (TTS) y ejecución de acciones del aplicativo.
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
    private var restartRunnable: Runnable? = null

    /**
     * Inicia o reinicia el ciclo de escucha para comandos de voz.
     */
    fun startListeningCycle(explicitPrompt: Boolean = false) {
        if (isDestroyed) return
        if (_state.value.isSpeaking || _state.value.isProcessing) return

        cancelScheduledRestart()

        if (explicitPrompt) {
            soundService.playButtonClickSound()
            vibrationService.vibrateStartListening()
        }

        _state.update {
            it.copy(
                isListening = true,
                feedbackMessage = if (explicitPrompt) "Escuchando tu orden..." else "Di 'Ok Asistente'..."
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
                // Si está en modo continuo y fue timeout o falta de match, reiniciar suavemente
                if (_state.value.isContinuousMode && !isDestroyed) {
                    scheduleListeningRestart(1200L)
                }
            }
        )
    }

    /**
     * Procesa la frase hablada por el usuario.
     */
    private fun processSpokenText(text: String, isExplicit: Boolean) {
        val lower = text.trim().lowercase()

        // Detectar si contiene palabras de activación ("asistente", "ok asistente", "oye asistente")
        // O si fue activado explícitamente mediante toque / botón
        val containsWakeWord = lower.contains("asistente") ||
                lower.contains("asistenta") ||
                lower.contains("asist") ||
                lower.contains("ok") ||
                lower.contains("oye")

        val isDirectCommand = isExplicit || containsWakeWord ||
                lower.startsWith("quien") ||
                lower.startsWith("que contactos") ||
                lower.startsWith("entra") ||
                lower.startsWith("abre") ||
                lower.startsWith("agrega") ||
                lower.startsWith("elimina") ||
                lower.startsWith("borra") ||
                lower.startsWith("dile a") ||
                lower.startsWith("enviar") ||
                lower.startsWith("cerrar")

        if (!isDirectCommand) {
            // No fue dirigido al asistente, reanudar escucha continua si corresponde
            if (_state.value.isContinuousMode && !isDestroyed) {
                scheduleListeningRestart(800L)
            }
            return
        }

        // Limpiar prefijos de llamada
        val cleanedQuery = text
            .replace("(?i)ok asistente".toRegex(), "")
            .replace("(?i)oye asistente".toRegex(), "")
            .replace("(?i)asistente".toRegex(), "")
            .trim()
            .ifBlank { text.trim() }

        soundService.playMessageReceivedSound()
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

            val result = GeminiDirectClient.processVoiceCommand(cleanedQuery, assistantContext)

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
                            feedbackMessage = "Di 'Ok Asistente' o toca para hablar"
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
