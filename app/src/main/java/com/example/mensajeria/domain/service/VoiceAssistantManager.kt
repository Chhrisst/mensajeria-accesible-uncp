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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    private companion object {
        const val TAG = "VoiceAssistantManager"
        const val IDLE_FEEDBACK = "Asistente de Voz IA"

        // Tope total para la consulta a Gemini (primario + respaldo). Pasado este tiempo se responde
        // con un mensaje de error hablado en lugar de dejar al usuario esperando en silencio.
        const val AI_TIMEOUT_MS = 20_000L

        // Red de seguridad: si el motor TTS nunca notifica el final, se vuelve a STANDBY igualmente.
        const val SPEAKING_WATCHDOG_BASE_MS = 8_000L
        const val SPEAKING_WATCHDOG_PER_CHAR_MS = 90L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Cualquier excepción no prevista dentro del asistente se registra y devuelve el estado a STANDBY
    // en vez de cerrar la aplicación durante la prueba con el usuario.
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Error no controlado en el asistente", throwable)
        mainHandler.post { resetToStandby(clearDialogue = true) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    private val _state = MutableStateFlow(VoiceAssistantState())
    val state: StateFlow<VoiceAssistantState> = _state.asStateFlow()

    // Historial para conversaciones continuas con el Asistente IA (solo se toca en el hilo principal)
    private val dialogueHistory = mutableListOf<Pair<String, String>>()
    private var pendingAction: String? = null

    // Consulta a la IA en curso; se cancela si el usuario detiene el asistente o lo reactiva.
    private var processingJob: Job? = null

    // Identifica la "sesión" de interacción vigente para descartar callbacks tardíos de sesiones anteriores.
    private var interactionToken = 0L

    private val reactivateRunnable = Runnable {
        if (!isDestroyed) activateAssistant()
    }
    private val speakingWatchdog = Runnable {
        if (_state.value.mode == AssistantMode.SPEAKING) {
            Log.w(TAG, "Watchdog: el TTS no notificó el final de la locución; volviendo a STANDBY.")
            resetToStandby(clearDialogue = true)
        }
    }

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
        mainHandler.removeCallbacks(reactivateRunnable)
        mainHandler.removeCallbacks(speakingWatchdog)
        processingJob?.cancel()
        processingJob = null
        if (_state.value.mode == AssistantMode.PROCESSING || _state.value.mode == AssistantMode.SPEAKING) {
            ttsService.stop()
        }

        val token = ++interactionToken

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
                if (token != interactionToken) return@startListening
                _state.update {
                    it.copy(
                        mode = AssistantMode.ACTIVE_LISTENING,
                        feedbackMessage = "Te escucho... Di tu orden"
                    )
                }
            },
            onResult = { recognizedText ->
                if (token != interactionToken || isDestroyed) return@startListening
                _state.update { it.copy(lastRecognizedText = recognizedText) }
                processCommandPhrase(recognizedText, token)
            },
            onError = { errorMsg ->
                if (token != interactionToken || isDestroyed) return@startListening
                Log.w(TAG, "Error de captura: $errorMsg")
                vibrationService.vibrateError()
                resetToStandby(clearDialogue = true)
                // El usuario invidente no ve la pantalla: el error debe oírse, no solo vibrar.
                ttsService.speak(errorMsg)
            }
        )
    }

    fun triggerExplicitActivation() = activateAssistant()

    /**
     * Procesa la orden del usuario enviándola a Gemini.
     */
    private fun processCommandPhrase(rawText: String, token: Long) {
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

        processingJob?.cancel()
        processingJob = scope.launch {
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

            // Se envía una COPIA del historial: la consulta corre en Dispatchers.IO y la lista original
            // puede modificarse en el hilo principal (stopAssistant), provocando ConcurrentModificationException.
            val historySnapshot = dialogueHistory.toList()

            val result = try {
                withTimeoutOrNull(AI_TIMEOUT_MS) {
                    GeminiDirectClient.processVoiceCommand(
                        spokenText = cleanCommand,
                        context = assistantContext,
                        dialogueHistory = historySnapshot
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fallo consultando a la IA", e)
                null
            } ?: VoiceCommandResult(
                action = "GENERAL_REPLY",
                spokenResponse = "No pude conectarme con el asistente en este momento. Revisa tu conexión a internet e inténtalo de nuevo."
            )

            // El usuario canceló o reactivó el asistente mientras se consultaba la IA
            if (token != interactionToken || isDestroyed) return@launch

            val spokenResponse = result.spokenResponse.ifBlank { "Entendido." }

            _state.update {
                it.copy(
                    mode = AssistantMode.SPEAKING,
                    feedbackMessage = spokenResponse
                )
            }

            // Ejecutar la mutación de estado o navegación. Un fallo aquí no debe dejar el asistente colgado.
            try {
                executeAction(result)
            } catch (e: Exception) {
                Log.e(TAG, "Error ejecutando la acción ${result.action}", e)
            }

            val isQuestion = spokenResponse.contains("?") ||
                    spokenResponse.contains("¿") ||
                    (result.action == "ADD_CONTACT" && (result.contactName.isNullOrBlank() && result.targetContact.isNullOrBlank())) ||
                    (result.action == "SEND_MESSAGE" && result.messageText.isNullOrBlank())

            mainHandler.removeCallbacks(speakingWatchdog)
            mainHandler.postDelayed(
                speakingWatchdog,
                SPEAKING_WATCHDOG_BASE_MS + spokenResponse.length * SPEAKING_WATCHDOG_PER_CHAR_MS
            )

            // Narrar la respuesta por voz al usuario
            ttsService.speak(
                spokenResponse,
                onDone = {
                    mainHandler.removeCallbacks(speakingWatchdog)
                    if (token != interactionToken || isDestroyed) return@speak
                    if (isQuestion) {
                        // Guardar en el historial de la conversación el turno para recordar la pregunta
                        dialogueHistory.add(cleanCommand to spokenResponse)
                        if (dialogueHistory.size > 6) {
                            dialogueHistory.removeAt(0)
                        }
                        // Esperar 400ms a que el hardware libere el audio y reactivar el micrófono para la respuesta del usuario
                        mainHandler.removeCallbacks(reactivateRunnable)
                        mainHandler.postDelayed(reactivateRunnable, 400)
                    } else {
                        resetToStandby(clearDialogue = true)
                    }
                },
                onInterrupted = {
                    // Otra locución (p. ej. exploración táctil) interrumpió la respuesta:
                    // no reabrir el micrófono, pero tampoco quedarse en SPEAKING.
                    mainHandler.removeCallbacks(speakingWatchdog)
                    if (token != interactionToken || isDestroyed) return@speak
                    if (_state.value.mode == AssistantMode.SPEAKING) {
                        resetToStandby(clearDialogue = false)
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

    private fun findContact(target: String): Contact? {
        val cleanTarget = target.trim()
        if (cleanTarget.isBlank()) return null
        return chatRepository.contacts.value.firstOrNull {
            it.name.isNotBlank() &&
                (it.name.contains(cleanTarget, ignoreCase = true) || cleanTarget.contains(it.name, ignoreCase = true))
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
                    val found = findContact(target)
                    if (found != null) {
                        onOpenChat?.invoke(found)
                    }
                }
            }

            "ADD_CONTACT" -> {
                val identifier = result.contactName ?: result.targetContact ?: ""
                val clean = identifier.trim().removePrefix("@")
                val currentUserId = getCurrentUserId()
                if (clean.isNotBlank()) {
                    pendingAction = null
                    scope.launch {
                        if (currentUserId.isNotBlank()) {
                            val addResult = chatRepository.addContactByUsername(currentUserId, clean)
                            addResult.fold(
                                onSuccess = { added ->
                                    val displayName = if (added.username.isNotBlank()) "@${added.username}" else added.name
                                    ttsService.speak("Contacto $displayName agregado exitosamente a tu lista.")
                                },
                                onFailure = { error ->
                                    val errorMsg = error.message ?: ""
                                    val speechError = if (errorMsg.contains("no existe", ignoreCase = true) || errorMsg.contains("404") || errorMsg.contains("No se encontró", ignoreCase = true)) {
                                        "El usuario arroba $clean no existe en el sistema UNCP. Por favor, verifica el nombre de usuario e intenta nuevamente."
                                    } else if (errorMsg.contains("mismo", ignoreCase = true)) {
                                        "No puedes agregarte a ti mismo como contacto."
                                    } else if (errorMsg.contains("ya existe", ignoreCase = true) || errorMsg.contains("ya está", ignoreCase = true)) {
                                        "El usuario arroba $clean ya está en tu lista de contactos."
                                    } else {
                                        "No se pudo agregar al usuario arroba $clean. Verifica que exista en la plataforma UNCP."
                                    }
                                    ttsService.speak(speechError)
                                }
                            )
                        } else {
                            ttsService.speak("Debes iniciar sesión para agregar contactos.")
                        }
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
                    Log.i(TAG, "Contacto eliminado: ${deleted?.name}")
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
                    val targetContact = if (!target.isNullOrBlank()) findContact(target) else null
                    if (targetContact != null) {
                        onOpenChat?.invoke(targetContact)
                    }
                    // Antes el mensaje solo se agregaba a la lista local y NUNCA llegaba al servidor.
                    val recipient = targetContact ?: getActiveChatContact()
                    val userId = getCurrentUserId()
                    if (recipient != null && recipient.id.isNotBlank() && userId.isNotBlank()) {
                        scope.launch {
                            val sendResult = chatRepository.sendMessageRemote(userId, recipient.id, text)
                            if (sendResult.isFailure) {
                                ttsService.speak(
                                    "Atención: el mensaje para ${recipient.name} no se pudo enviar por falta de conexión. Quedó guardado en el teléfono.",
                                    queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD
                                )
                            }
                        }
                    } else {
                        chatRepository.sendMessage(text)
                    }
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

    private fun resetToStandby(clearDialogue: Boolean) {
        if (clearDialogue) {
            dialogueHistory.clear()
            pendingAction = null
        }
        _state.update {
            it.copy(
                mode = AssistantMode.STANDBY,
                feedbackMessage = IDLE_FEEDBACK
            )
        }
    }

    fun stopAssistant() {
        // Invalidar callbacks tardíos (reconocimiento, IA o TTS) de la interacción en curso
        interactionToken++
        mainHandler.removeCallbacks(reactivateRunnable)
        mainHandler.removeCallbacks(speakingWatchdog)
        processingJob?.cancel()
        processingJob = null
        speechService.stopListening()
        ttsService.stop()
        resetToStandby(clearDialogue = true)
    }

    fun destroy() {
        isDestroyed = true
        stopAssistant()
        scope.cancel()
    }
}
