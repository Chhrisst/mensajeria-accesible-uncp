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
    val voiceDurationSeconds: Int = 0
)

/**
 * Modelo para contactos con los que interactúa el usuario.
 */
data class Contact(
    val id: String,
    val name: String,
    val description: String = "",
    val isOnline: Boolean = true
)

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
    val contact: Contact = Contact("c1", "Contacto de Prueba"),
    val messages: List<Message> = emptyList(),
    val voiceState: VoiceState = VoiceState.IDLE,
    val pendingSpokenText: String = "",
    val smartReplies: List<String> = emptyList(),
    val feedbackMessage: String = "",
    val isTtsActive: Boolean = true
)

/**
 * Perfil de usuario o socio registrado en el sistema UNCP.
 */
data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val email: String = "",
    val phone: String = "",
    val dni: String = "",
    val birthDate: String = "",
    val code: String = "123", // PIN numérico
    val sidePattern: String = "LRL", // Patrón de toques laterales: L (Izquierda), R (Derecha)
    val role: String = "Socio Activo UNCP",
    val description: String = "",
    val hasDisability: Boolean = true,
    val visualCondition: String = "Ceguera total" // "Ceguera total", "Baja visión", "Otra condición visual"
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
    val dni: String = "",
    val birthDate: String = "",
    val phone: String = "",
    val email: String = "",
    val hasDisability: Boolean = true,
    val visualCondition: String = "Ceguera total",
    val pin: String = "123"
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
    val errorMessage: String? = null
)


