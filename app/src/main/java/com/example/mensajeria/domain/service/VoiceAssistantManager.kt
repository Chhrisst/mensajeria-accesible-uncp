package com.example.mensajeria.domain.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.mensajeria.data.api.ApiUserResponse
import com.example.mensajeria.data.api.GeminiDirectClient
import com.example.mensajeria.data.api.RetrofitClient
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Modos de interacción del Asistente de Voz Inteligente.
 */
enum class AssistantMode {
    STANDBY,           // En reposo: Micrófono 100% apagado.
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
    val feedbackMessage: String = "Pulsa aquí para hablar con el Asistente IA"
)

/**
 * Gestor del Asistente de Voz Inteligente con Gemini.
 * Soporta diálogos multi-turno sin cortar el micrófono, responde preguntas cotidianas
 * (hora, clima/grados, @usuario) e indica claramente cómo volver a activar la voz.
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
        const val IDLE_FEEDBACK = "Pulsa el botón para hablar con el Asistente IA"
        const val AI_TIMEOUT_MS = 20_000L
        const val SPEAKING_WATCHDOG_BASE_MS = 10_000L
        const val SPEAKING_WATCHDOG_PER_CHAR_MS = 95L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val weatherHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedWeatherText: String? = null
    @Volatile private var cachedWeatherTimestamp: Long = 0L

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Error no controlado en el asistente", throwable)
        mainHandler.post { resetToStandby(clearDialogue = true) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    private val _state = MutableStateFlow(VoiceAssistantState())
    val state: StateFlow<VoiceAssistantState> = _state.asStateFlow()

    private val dialogueHistory = mutableListOf<Pair<String, String>>()
    private var pendingAction: String? = null
    private var pendingTargetContact: String? = null
    private var processingJob: Job? = null
    private var interactionToken = 0L

    private val reactivateRunnable = Runnable {
        if (!isDestroyed) startListeningTurn(isFollowUp = true)
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
    var onStartVoiceNote: (() -> Unit)? = null
    var onSummarizeLastMessage: (() -> Unit)? = null
    var onStartCall: (() -> Unit)? = null
    var getCurrentScreen: () -> String = { "CONTACTS" }
    var getCurrentUser: () -> String = { "Usuario" }
    var getCurrentUsername: () -> String = { "" }
    var getCurrentUserId: () -> String = { "" }
    var getActiveChatContact: () -> Contact? = { null }

    @Volatile var isEnabled: Boolean = true
    private var isDestroyed = false

    fun setAssistantEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) {
            stopAssistant()
        }
    }

    fun activateAssistant() {
        if (isDestroyed || !isEnabled) return
        startListeningTurn(isFollowUp = false)
    }

    fun triggerExplicitActivation() = activateAssistant()

    private fun startListeningTurn(isFollowUp: Boolean) {
        if (isDestroyed || !isEnabled) return
        mainHandler.removeCallbacks(reactivateRunnable)
        mainHandler.removeCallbacks(speakingWatchdog)
        processingJob?.cancel()
        processingJob = null
        if (!isFollowUp) {
            ttsService.stop()
        }

        val token = ++interactionToken
        vibrationService.vibrateStartListening()

        _state.update {
            it.copy(
                mode = AssistantMode.ACTIVE_LISTENING,
                feedbackMessage = "Habla después del tono..."
            )
        }

        speechService.startListening(
            onReady = {
                if (token != interactionToken) return@startListening
                _state.update {
                    it.copy(
                        mode = AssistantMode.ACTIVE_LISTENING,
                        feedbackMessage = "Escuchando tu voz..."
                    )
                }
            },
            onResult = { recognizedText ->
                if (token != interactionToken || isDestroyed) return@startListening
                _state.update { it.copy(lastRecognizedText = recognizedText) }
                processCommandPhrase(recognizedText, token)
            },
            onError = { _ ->
                if (token != interactionToken || isDestroyed) return@startListening
                vibrationService.vibrateError()
                resetToStandby(clearDialogue = true)
                ttsService.speak(
                    "No logré escucharte bien. Pulsa el botón del asistente de voz para volver a intentar, y habla después del tono."
                )
            },
            playPromptTone = true
        )
    }

    private fun processCommandPhrase(rawText: String, token: Long) {
        if (isDestroyed || !isEnabled || token != interactionToken) {
            stopAssistant()
            return
        }
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
                feedbackMessage = "Procesando tu solicitud..."
            )
        }

        processingJob?.cancel()
        processingJob = scope.launch {
            val currentTimeText = formatCurrentDateTimeForVoice()
            val weatherText = fetchWeatherSummary()

            // Atajo instantáneo y preciso para preguntas comunes y acciones directas/multi-turno
            val localCommonReply = tryHandleCommonQuestionLocally(
                query = cleanCommand,
                currentTimeText = currentTimeText,
                weatherText = weatherText
            ) ?: tryHandleActionCommandLocally(cleanCommand)

            val result = if (localCommonReply != null) {
                localCommonReply
            } else {
                val contactsList = chatRepository.contacts.value.map {
                    if (it.username.isNotBlank()) "${it.name} (@${it.username})" else it.name
                }
                val activeChat = getActiveChatContact()
                val recentMessages = chatRepository.messages.value.takeLast(4).map { it.text }

                val assistantContext = VoiceAssistantContext(
                    currentScreen = getCurrentScreen(),
                    currentUserName = getCurrentUser(),
                    currentUsername = getCurrentUsername(),
                    currentTime = currentTimeText,
                    currentWeather = weatherText,
                    contacts = contactsList,
                    activeChatContact = activeChat?.name,
                    recentMessages = recentMessages
                )

                val historySnapshot = dialogueHistory.toList()

                try {
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
                    spokenResponse = "No pude conectarme con la inteligencia artificial. Revisa tu conexión e inténtalo de nuevo."
                )
            }

            if (token != interactionToken || isDestroyed) return@launch

            val rawSpoken = result.spokenResponse.ifBlank { "Entendido." }

            _state.update {
                it.copy(
                    mode = AssistantMode.SPEAKING,
                    feedbackMessage = rawSpoken
                )
            }

            // Si la acción toma el micrófono directo (ej: grabar nota de voz), no reabrir STT después
            val handsOverMicToVoiceNote = (result.action == "SEND_VOICE_NOTE" && getActiveChatContact() != null)

            try {
                executeAction(result)
            } catch (e: Exception) {
                Log.e(TAG, "Error ejecutando la acción ${result.action}", e)
            }

            if (handsOverMicToVoiceNote) {
                resetToStandby(clearDialogue = true)
                return@launch
            }

            val isQuestion = !rawSpoken.contains("Para hablar nuevamente", ignoreCase = true) && (
                rawSpoken.trim().endsWith("?") ||
                    rawSpoken.contains("¿") ||
                    (result.action == "ADD_CONTACT" && result.contactName.isNullOrBlank() && result.targetContact.isNullOrBlank()) ||
                    (result.action == "SEND_MESSAGE" && result.messageText.isNullOrBlank())
                )

            if (isQuestion) {
                if (result.action == "SEND_MESSAGE" && result.messageText.isNullOrBlank()) {
                    pendingAction = "SEND_MESSAGE"
                    pendingTargetContact = result.targetContact ?: getActiveChatContact()?.name
                } else if (result.action == "ADD_CONTACT" && result.contactName.isNullOrBlank() && result.targetContact.isNullOrBlank()) {
                    pendingAction = "ADD_CONTACT"
                }
            } else {
                pendingAction = null
                pendingTargetContact = null
            }

            val speechToSpeak = if (isQuestion) {
                if (rawSpoken.contains("después del tono", ignoreCase = true)) rawSpoken
                else "$rawSpoken Habla después del tono."
            } else {
                if (rawSpoken.contains("pulsa el botón", ignoreCase = true)) rawSpoken
                else "$rawSpoken Para hablar nuevamente, pulsa el botón del asistente de voz."
            }

            mainHandler.removeCallbacks(speakingWatchdog)
            mainHandler.postDelayed(
                speakingWatchdog,
                SPEAKING_WATCHDOG_BASE_MS + speechToSpeak.length * SPEAKING_WATCHDOG_PER_CHAR_MS
            )

            ttsService.speak(
                speechToSpeak,
                onDone = {
                    mainHandler.removeCallbacks(speakingWatchdog)
                    if (token != interactionToken || isDestroyed) return@speak
                    if (isQuestion) {
                        dialogueHistory.add(cleanCommand to rawSpoken)
                        if (dialogueHistory.size > 6) {
                            dialogueHistory.removeAt(0)
                        }
                        // Esperar 550ms para que TTS libere el audio antes del tono y micrófono
                        mainHandler.removeCallbacks(reactivateRunnable)
                        mainHandler.postDelayed(reactivateRunnable, 550L)
                    } else {
                        resetToStandby(clearDialogue = true)
                    }
                },
                onInterrupted = {
                    mainHandler.removeCallbacks(speakingWatchdog)
                    if (token != interactionToken || isDestroyed) return@speak
                    if (_state.value.mode == AssistantMode.SPEAKING) {
                        resetToStandby(clearDialogue = false)
                    }
                }
            )
        }
    }

    private fun tryHandleCommonQuestionLocally(
        query: String,
        currentTimeText: String,
        weatherText: String
    ): VoiceCommandResult? {
        val q = query.lowercase(Locale.getDefault())
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")

        if (q.contains("que hora") || q.contains("la hora") || q.contains("que dia es") || q.contains("fecha de hoy")) {
            return VoiceCommandResult(
                action = "GENERAL_REPLY",
                spokenResponse = "Actualmente son $currentTimeText."
            )
        }

        if (q.contains("cuantos grados") || q.contains("temperatura") || q.contains("clima") || q.contains("hace frio") || q.contains("hace calor")) {
            return VoiceCommandResult(
                action = "GENERAL_REPLY",
                spokenResponse = weatherText
            )
        }

        if (q.contains("nombre de usuario") || q.contains("mi usuario") || q.contains("como me llamo") || q.contains("quien soy") || q.contains("cual es mi cuenta")) {
            val fullName = getCurrentUser().ifBlank { "Usuario" }
            val handle = getCurrentUsername().ifBlank { fullName.lowercase().replace(" ", "") }
            return VoiceCommandResult(
                action = "GENERAL_REPLY",
                spokenResponse = "Tu nombre es $fullName y tu nombre de usuario único es arroba $handle."
            )
        }

        return null
    }

    private fun tryHandleActionCommandLocally(rawCommand: String): VoiceCommandResult? {
        val trimmed = rawCommand.trim()
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase(Locale.getDefault())
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")

        if (lower in setOf("cancelar", "cancela", "olvidalo", "nada", "dejalo")) {
            pendingAction = null
            pendingTargetContact = null
            return VoiceCommandResult(
                action = "GENERAL_REPLY",
                spokenResponse = "Operación cancelada."
            )
        }

        // Respuesta directa a pregunta pendiente del asistente en diálogo multi-turno
        val currentPending = pendingAction
        if (currentPending == "SEND_MESSAGE") {
            val target = pendingTargetContact ?: getActiveChatContact()?.name
            pendingAction = null
            pendingTargetContact = null
            val polished = IntonationPunctuationPolisher.polish(trimmed)
            return VoiceCommandResult(
                action = "SEND_MESSAGE",
                targetContact = target,
                messageText = polished,
                spokenResponse = "Mensaje enviado: $polished"
            )
        }
        if (currentPending == "ADD_CONTACT") {
            pendingAction = null
            val cleanUsername = trimmed
                .replace(Regex("(?i)^(?:su\\s+usuario\\s+es|el\\s+usuario\\s+es|es\\s+el\\s+usuario|es|al\\s+usuario|el\\s+usuario|usuario|arroba)\\s+"), "")
                .replace("@", "")
                .replace(" ", "")
                .lowercase()
            return VoiceCommandResult(
                action = "ADD_CONTACT",
                contactName = cleanUsername,
                spokenResponse = "Buscando al usuario arroba $cleanUsername para agregarlo."
            )
        }

        // Agregar contacto por nombre de usuario (flujo directo o conversacional multi-turno)
        val directAddUser = extractDirectUsernameFromAddCommand(trimmed)
        if (!directAddUser.isNullOrBlank()) {
            val cleanUser = directAddUser.removePrefix("@").replace(" ", "").lowercase()
            return VoiceCommandResult(
                action = "ADD_CONTACT",
                contactName = cleanUser,
                spokenResponse = "Buscando al usuario arroba $cleanUser para agregarlo."
            )
        }

        val isAddContactIntent = lower in setOf(
            "agrega un contacto", "agregar un contacto", "quiero agregar un contacto",
            "agrega contacto", "agregar contacto", "anadir contacto", "añadir contacto", "nuevo contacto",
            "agrega un usuario", "agregar un usuario", "agregar usuario", "agrega usuario", "nuevo usuario",
            "anadir un contacto", "añadir un contacto", "quiero agregar a alguien", "agregar a alguien",
            "agrega a alguien", "agregar", "agrega"
        ) || (lower.startsWith("agrega un contacto") || lower.startsWith("agregar un contacto") ||
              lower.startsWith("quiero agregar un contacto") || lower.startsWith("anadir contacto") ||
              lower.startsWith("añadir contacto"))

        if (isAddContactIntent) {
            pendingAction = "ADD_CONTACT"
            return VoiceCommandResult(
                action = "ADD_CONTACT",
                contactName = null,
                spokenResponse = "¿Cuál es el nombre de usuario del contacto que deseas agregar?"
            )
        }

        val activeChat = getActiveChatContact()
        val isInChat = getCurrentScreen() == "CHAT" && activeChat != null

        // Nota de voz directa (evaluar antes que "enviar")
        if (isInChat && (lower.contains("nota de voz") || lower.contains("graba un audio") || lower.contains("grabar audio") || lower.contains("enviar audio") || lower.contains("envia audio"))) {
            return VoiceCommandResult(
                action = "SEND_VOICE_NOTE",
                spokenResponse = "Iniciando grabación de nota de voz. Habla y luego toca enviar."
            )
        }

        // Lectura rápida de mensajes recientes
        if (isInChat && (lower.contains("leer mensajes") || lower.contains("lee los mensajes") || lower.contains("ultimo mensaje") || lower.contains("que me dijo"))) {
            val recent = chatRepository.messages.value.takeLast(3)
            val reply = if (recent.isEmpty()) {
                "Aún no hay mensajes en esta conversación."
            } else {
                recent.joinToString(". ") { msg ->
                    val who = if (msg.isOutgoing) "Tú dijiste" else "${activeChat?.name ?: "Contacto"} dijo"
                    "$who: ${msg.text}"
                }
            }
            return VoiceCommandResult(
                action = "READ_MESSAGES",
                spokenResponse = reply
            )
        }

        // Llamada de voz directa
        if (lower.startsWith("llama a ") || lower.startsWith("llamar a ") || lower == "llamar" || lower == "hacer una llamada" || lower == "iniciar llamada") {
            val targetName = trimmed
                .replace(Regex("(?i)^(llama|llamar)\\s+a\\s+"), "")
                .takeIf { !lower.startsWith("hacer") && !lower.startsWith("iniciar") && lower != "llamar" }
            val resolvedTarget = if (!targetName.isNullOrBlank()) findContact(targetName)?.name ?: targetName else activeChat?.name
            if (resolvedTarget != null) {
                return VoiceCommandResult(
                    action = "START_CALL",
                    targetContact = resolvedTarget,
                    spokenResponse = "Iniciando llamada con $resolvedTarget."
                )
            }
        }

        // Envío directo de mensaje dentro del chat con prefijos comunes
        if (isInChat) {
            val chatSendPrefixes = listOf(
                Regex("(?i)^env[ií]a(?:r)?\\s+(?:un\\s+)?mensaje\\s+(?:que\\s+diga|diciendo)\\s+(.+)$"),
                Regex("(?i)^env[ií]a(?:r)?\\s+el\\s+mensaje\\s+(.+)$"),
                Regex("(?i)^(?:dile|di|env[ií]a|enviar|manda|mandar|escribe|escribir)\\s+(.+)$")
            )
            for (regex in chatSendPrefixes) {
                val match = regex.matchEntire(trimmed)
                val body = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
                if (body.isNotBlank() && !body.contains("nota de voz", ignoreCase = true) && !body.startsWith("un mensaje", ignoreCase = true)) {
                    val polished = IntonationPunctuationPolisher.polish(body)
                    return VoiceCommandResult(
                        action = "SEND_MESSAGE",
                        targetContact = activeChat?.name,
                        messageText = polished,
                        spokenResponse = "Mensaje enviado: $polished"
                    )
                }
            }
        }

        return null
    }

    private fun formatCurrentDateTimeForVoice(): String {
        val now = Date()
        val timeFmt = SimpleDateFormat("h:mm a", Locale("es", "PE")).format(now)
        val dateFmt = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "PE")).format(now)
        return "las $timeFmt del $dateFmt"
    }

    private suspend fun fetchWeatherSummary(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedWeatherText
        if (cached != null && now - cachedWeatherTimestamp < 15 * 60 * 1000L) {
            return@withContext cached
        }
        val fetched = try {
            val req = Request.Builder()
                .url("https://api.open-meteo.com/v1/forecast?latitude=-12.0651&longitude=-75.2049&current_weather=true")
                .build()
            weatherHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val current = JSONObject(body).optJSONObject("current_weather")
                    if (current != null && current.has("temperature")) {
                        val temp = Math.round(current.getDouble("temperature")).toInt()
                        "Actualmente estamos a $temp grados centígrados."
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val approxTemp = when (hour) {
            in 6..10 -> 14
            in 11..16 -> 19
            in 17..20 -> 15
            else -> 11
        }
        val finalResult = fetched ?: "Estamos aproximadamente a $approxTemp grados centígrados."
        cachedWeatherText = finalResult
        cachedWeatherTimestamp = now
        finalResult
    }

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

    private fun extractDirectUsernameFromAddCommand(text: String): String? {
        val trimmed = text.trim()
        val patterns = listOf(
            Regex("(?i)^(?:agrega|agregar|anadir|añadir)\\s+(?:al\\s+usuario|el\\s+usuario|a\\s+usuario|usuario)\\s+[@]?([a-zA-Z0-9_]+)$"),
            Regex("(?i)^(?:agrega|agregar|anadir|añadir)\\s+(?:un\\s+contacto|el\\s+contacto|a\\s+un\\s+contacto|contacto)\\s+(?:con\\s+usuario\\s+|con\\s+el\\s+usuario\\s+)?(?:al\\s+usuario\\s+)?(?:arroba\\s+)?[@]?([a-zA-Z0-9_]+)$"),
            Regex("(?i)^(?:agrega|agregar|anadir|añadir)\\s+a\\s+[@]?([a-zA-Z0-9_]+)$"),
            Regex("(?i)^(?:agrega|agregar|anadir|añadir)\\s+[@]([a-zA-Z0-9_]+)$")
        )
        for (pattern in patterns) {
            val match = pattern.matchEntire(trimmed)
            val extracted = match?.groupValues?.getOrNull(1)?.trim()
            if (!extracted.isNullOrBlank()) {
                val lower = extracted.lowercase()
                if (lower !in setOf("contacto", "un", "el", "usuario", "alguien", "amigo", "nueva", "nuevo")) {
                    return extracted
                }
            }
        }
        return null
    }

    private fun findContact(target: String): Contact? {
        val cleanTarget = target.trim().removePrefix("@")
        if (cleanTarget.isBlank()) return null
        val currentContacts = chatRepository.contacts.value
        return currentContacts.firstOrNull {
            it.username.isNotBlank() && it.username.equals(cleanTarget, ignoreCase = true)
        } ?: currentContacts.firstOrNull {
            it.name.isNotBlank() &&
                (it.name.contains(cleanTarget, ignoreCase = true) || cleanTarget.contains(it.name, ignoreCase = true))
        }
    }

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
                val clean = identifier.trim()
                    .replace(Regex("(?i)^(?:su\\s+usuario\\s+es|el\\s+usuario\\s+es|es\\s+el\\s+usuario|es|al\\s+usuario|el\\s+usuario|usuario|arroba)\\s+"), "")
                    .removePrefix("@")
                    .replace(" ", "")
                    .lowercase()
                val currentUserId = getCurrentUserId()
                if (clean.isNotBlank()) {
                    pendingAction = null
                    scope.launch {
                        if (currentUserId.isNotBlank()) {
                            var targetUsername = clean
                            try {
                                val usersResp = RetrofitClient.api.getUsers()
                                val usersList: List<ApiUserResponse>? = usersResp.body()
                                if (usersResp.isSuccessful && !usersList.isNullOrEmpty()) {
                                    val cleanLower = clean.lowercase().trim()
                                    // Búsqueda estricta por nombre de usuario único (@usuario o nickname)
                                    val matched = usersList.firstOrNull { u ->
                                        (u.username ?: "").lowercase().trim() == cleanLower
                                    }
                                    if (matched != null && !matched.username.isNullOrBlank()) {
                                        targetUsername = matched.username
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "No se pudo consultar lista de usuarios previa: ${e.message}")
                            }

                            val addResult = chatRepository.addContactByUsername(currentUserId, targetUsername)
                            addResult.fold(
                                onSuccess = { added ->
                                    val displayName = if (added.name.isNotBlank() && !added.name.equals(added.username, ignoreCase = true)) {
                                        "${added.name} con usuario arroba ${added.username}"
                                    } else {
                                        "arroba ${added.username}"
                                    }
                                    ttsService.speak(
                                        "Contacto $displayName agregado exitosamente. Para hablar nuevamente, pulsa el botón del asistente de voz."
                                    )
                                },
                                onFailure = { _ ->
                                    ttsService.speak(
                                        "No encontré a ningún usuario registrado con el usuario arroba $clean. Para hablar nuevamente, pulsa el botón del asistente de voz."
                                    )
                                }
                            )
                        } else {
                            ttsService.speak("No se pudo agregar el contacto porque no hay una sesión activa.")
                        }
                    }
                } else {
                    pendingAction = "ADD_CONTACT"
                }
            }

            "DELETE_CONTACT" -> {
                val target = result.targetContact ?: result.contactName ?: ""
                if (target.isNotBlank()) {
                    chatRepository.deleteContact(target)
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
                val rawText = result.messageText
                if (!rawText.isNullOrBlank()) {
                    val polishedText = IntonationPunctuationPolisher.polish(rawText)
                    val target = result.targetContact
                    val targetContact = if (!target.isNullOrBlank()) findContact(target) else null
                    val currentActive = getActiveChatContact()
                    if (targetContact != null && (getCurrentScreen() != "CHAT" || currentActive?.id != targetContact.id)) {
                        onOpenChat?.invoke(targetContact)
                    }
                    val recipient = targetContact ?: currentActive
                    val userId = getCurrentUserId()
                    if (recipient != null && recipient.id.isNotBlank() && userId.isNotBlank()) {
                        scope.launch {
                            val sendResult = chatRepository.sendMessageRemote(userId, recipient.id, polishedText)
                            if (sendResult.isFailure) {
                                ttsService.speak(
                                    "Atención: el mensaje para ${recipient.name} no se pudo enviar por falta de conexión. Quedó guardado en el teléfono.",
                                    queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD
                                )
                            }
                        }
                    } else {
                        chatRepository.sendMessage(polishedText)
                    }
                }
            }

            "SEND_VOICE_NOTE" -> {
                onStartVoiceNote?.invoke()
            }

            "SUMMARIZE_MESSAGES" -> {
                onSummarizeLastMessage?.invoke()
            }

            "START_CALL" -> {
                val target = result.targetContact
                if (!target.isNullOrBlank()) {
                    findContact(target)?.let { onOpenChat?.invoke(it) }
                }
                onStartCall?.invoke()
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
            pendingTargetContact = null
        }
        _state.update {
            it.copy(
                mode = AssistantMode.STANDBY,
                feedbackMessage = IDLE_FEEDBACK
            )
        }
    }

    fun stopAssistant() {
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
