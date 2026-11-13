package com.example.mensajeria.data.model

import java.util.UUID

/**
 * Modelo inmutable para mensajes dentro de la conversación.
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isOutgoing: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val senderName: String = if (isOutgoing) "Tú" else "Contacto",
    val isVoiceNote: Boolean = false,
    val voiceDurationSeconds: Int = 0,
    val audioBase64: String = "",
    val formattedTime: String = ""
)

/**
 * Modelo para contactos con los que interactúa el usuario.
 */
data class Contact(
    val id: String,
    val name: String,
    val username: String = "",
    val description: String = "",
    val isOnline: Boolean = false,
    val lastSeenTime: String = ""
) {
    fun getLastSeenDisplay(): String {
        val raw = lastSeenTime.trim()
        if (Regex("^\\d{1,2}:\\d{2}$").matches(raw)) {
            return raw
        }
        // Si no hay hora explícita del servidor, derivar una hora reciente estable del día
        val cal = java.util.Calendar.getInstance()
        val seed = (id.hashCode() and 0x7FFFFFFF) % 45 + 3
        cal.add(java.util.Calendar.MINUTE, -seed)
        return String.format(
            java.util.Locale.getDefault(),
            "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }

    fun getConnectionStatusText(): String {
        return if (isOnline) "En línea" else "Conectado última vez a la(s) ${getLastSeenDisplay()}"
    }
}

/**
 * Estados del flujo de interacción por voz y accesibilidad.
 */
enum class VoiceState {
    IDLE,        // En reposo
    LISTENING,   // Micrófono capturando voz del usuario
    CONFIRMING,  // Mensaje reconocido, esperando confirmación auditiva del usuario
    SPEAKING,    // El sistema está narrando por TTS
    ERROR        // Ocurrió un fallo en el reconocimiento
}

/**
 * Estado UI global reactivo para la pantalla de chat (MVVM UDF).
 */
data class ChatUiState(
    val contact: Contact = Contact(id = "", name = "", username = ""),
    val messages: List<Message> = emptyList(),
    val voiceState: VoiceState = VoiceState.IDLE,
    val pendingSpokenText: String = "",
    val smartReplies: List<String> = emptyList(),
    val feedbackMessage: String = "",
    val isTtsActive: Boolean = true,
    val isRecordingVoiceNote: Boolean = false,
    val recordingSeconds: Int = 0,
    val playingMessageId: String? = null,
    val playbackProgress: Float = 0f,
    val isInCall: Boolean = false,
    val callStatusText: String = "",
    val callDurationSeconds: Int = 0,
    val isCallMuted: Boolean = false,
    val isSpeakerphoneOn: Boolean = true
)

/**
 * Perfil de usuario o socio registrado en el sistema UNCP.
 */
data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val dni: String = "",
    val birthDate: String = "",
    val code: String = "1234", // PIN numérico (4 dígitos)
    val sidePattern: String = "LRLR", // Patrón de toques laterales: L (Izquierda), R (Derecha) (4 toques)
    val role: String = "Usuario",
    val description: String = "Con discapacidad",
    val hasDisability: Boolean = true,
    val visualCondition: String = "Con discapacidad"
)

/**
 * Método de registro seleccionado por el usuario.
 */
enum class RegisterMethod {
    EMAIL,
    PHONE
}

/**
 * Pasos del Stepper interactivo de registro accesible.
 */
enum class RegisterStep {
    METHOD_SELECTION,
    PERSONAL_DATA,
    CONTACT_INFO,
    DISABILITY_QUESTION,
    VISUAL_CONDITION,
    SUMMARY_REVIEW,
    COMPLETED
}

/**
 * Formulario de registro en progreso.
 */
data class RegistrationForm(
    val method: RegisterMethod = RegisterMethod.EMAIL,
    val fullName: String = "",
    val username: String = "",
    val dni: String = "",
    val birthDate: String = "",
    val phone: String = "",
    val email: String = "",
    val hasDisability: Boolean = true,
    val visualCondition: String = "Ceguera total",
    val pin: String = "1234"
)

/**
 * Fases del flujo de autenticación asistida por voz y PIN.
 */
enum class VoiceAuthPhase {
    IDLE,
    LISTENING_NAME,
    ACCOUNT_DETECTED,
    LISTENING_PIN,
    SUCCESS,
    ERROR
}

/**
 * Estado UI reactivo para el flujo de autenticación por voz.
 */
data class VoiceAuthState(
    val phase: VoiceAuthPhase = VoiceAuthPhase.IDLE,
    val detectedUser: UserProfile? = null,
    val enteredPin: String = "",
    val errorMessage: String = "",
    val feedbackMessage: String = ""
)

/**
 * Estado UI reactivo para la pantalla de inicio de sesión accesible.
 */
data class LoginUiState(
    val authenticatedUser: UserProfile? = null,
    val voiceState: VoiceState = VoiceState.IDLE,
    val recognizedText: String = "",
    val feedbackMessage: String = "Bienvenido a Asistente de Voz. Inicie sesión para continuar.",
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

/**
 * Contexto actual enviado al agente de IA Gemini para procesar comandos de voz.
 */
data class VoiceAssistantContext(
    val currentScreen: String = "CONTACTS", // "CONTACTS", "CHAT"
    val currentUserName: String = "",
    val currentUsername: String = "",
    val currentTime: String = "",
    val currentWeather: String = "",
    val contacts: List<String> = emptyList(),
    val activeChatContact: String? = null,
    val recentMessages: List<String> = emptyList()
)

/**
 * Resultado estructurado devuelto por la IA para ejecutar acciones accesibles.
 */
data class VoiceCommandResult(
    val action: String = "GENERAL_REPLY", // "OPEN_CHAT", "LIST_CONTACTS", "ADD_CONTACT", "DELETE_CONTACT", "MODIFY_CONTACT", "SEND_MESSAGE", "READ_MESSAGES", "LOGOUT", "BACK", "HELP", "GENERAL_REPLY"
    val targetContact: String? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val messageText: String? = null,
    val spokenResponse: String
)



