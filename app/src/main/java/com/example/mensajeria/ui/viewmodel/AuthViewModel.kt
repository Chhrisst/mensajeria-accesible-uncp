package com.example.mensajeria.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mensajeria.data.api.RetrofitClient
import com.example.mensajeria.data.model.LoginUiState
import com.example.mensajeria.data.model.RegisterMethod
import com.example.mensajeria.data.model.RegisterStep
import com.example.mensajeria.data.model.RegistrationForm
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.data.model.VoiceAuthPhase
import com.example.mensajeria.data.model.VoiceAuthState
import com.example.mensajeria.data.model.VoiceState
import com.example.mensajeria.data.repository.AuthRepository
import com.example.mensajeria.domain.service.SpeechService
import com.example.mensajeria.domain.service.TextToSpeechService
import com.example.mensajeria.domain.service.VibrationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel que gestiona la autenticación, flujos de voz de Figma y registro interactivo.
 */
class AuthViewModel(
    context: Context,
    val authRepository: AuthRepository = AuthRepository(context)
) : ViewModel() {

    val speechService = SpeechService(context)
    val vibrationService = com.example.mensajeria.domain.service.VibrationService.getInstance(context)
    val soundService = com.example.mensajeria.domain.service.SoundNotificationService.getInstance(context)
    val ttsService = TextToSpeechService(context) {
        // Inicializado correctamente sin disparar audio automático
    }

    // Estado de login general
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Estado del flujo de autenticación por voz (Figma Canvas.png & Canvas-1.png)
    private val _voiceAuthState = MutableStateFlow(VoiceAuthState())
    val voiceAuthState: StateFlow<VoiceAuthState> = _voiceAuthState.asStateFlow()

    // Estado del formulario de registro de 4 pasos (Figma Canvas-2.png)
    private val _registrationForm = MutableStateFlow(RegistrationForm())
    val registrationForm: StateFlow<RegistrationForm> = _registrationForm.asStateFlow()

    private val _registerStep = MutableStateFlow(RegisterStep.METHOD_SELECTION)
    val registerStep: StateFlow<RegisterStep> = _registerStep.asStateFlow()

    fun speakWelcome() {
        val welcome = "Bienvenido a Asistente de Voz. Inicia sesión para continuar y acceder a tus mensajes, o toca el micrófono central para activar la entrada por voz."
        ttsService.speak(welcome)
    }

    fun speakText(text: String) {
        ttsService.speak(text)
    }

    // ----------------------------------------------------
    // FLUJO DE AUTENTICACIÓN POR VOZ (FIGMA CANVAS.PNG)
    // ----------------------------------------------------

    /**
     * Paso 1: Pantalla de Bienvenida y Activación Automática (Canvas.png)
     * "Asistente de Voz Activo - Diga el nombre de su cuenta para iniciar"
     */
    fun startVoiceAuth() {
        ttsService.stop()
        _voiceAuthState.value = VoiceAuthState(
            phase = VoiceAuthPhase.LISTENING_NAME,
            feedbackMessage = "Diga el nombre de su cuenta para iniciar"
        )
        speechService.startListening(
            onReady = {},
            onResult = { recognizedName ->
                processVoiceAccountName(recognizedName)
            },
            onError = { error ->
                triggerVoiceError("No se pudo reconocer el nombre. Por favor, intente nuevamente.")
            }
        )
    }

    /**
     * Dictado directo por voz para registro de nuevo usuario.
     */
    fun startRegistrationVoiceInput(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        ttsService.stop()
        speechService.startListening(
            onReady = {},
            onResult = { recognizedText ->
                soundService.playSuccessSound()
                vibrationService.vibrateSuccess()
                onResult(recognizedText)
            },
            onError = { errorMsg ->
                onError(errorMsg)
            }
        )
    }

    /**
     * Paso 2: Selección de Cuenta por Voz (Modo Seguro)
     * "Cuenta detectada. Escuchando PIN..."
     */
    private fun processVoiceAccountName(recognizedName: String) {
        val found = authRepository.findUserByQuery(recognizedName)
        if (found != null) {
            handleAccountDetected(found)
        } else {
            viewModelScope.launch {
                try {
                    val response = RetrofitClient.api.getUsers()
                    if (response.isSuccessful && response.body() != null) {
                        val clean = recognizedName.trim().lowercase()
                        val match = response.body()!!.firstOrNull { u ->
                            val uName = u.name.trim().lowercase()
                            uName.contains(clean) || clean.contains(uName) ||
                            clean.replace(" ", "").contains(uName.replace(" ", "")) ||
                            uName.replace(" ", "").contains(clean.replace(" ", ""))
                        }
                        if (match != null) {
                            val desc = if (match.hasDisability) "Con discapacidad" else "Sin discapacidad"
                            val profile = UserProfile(
                                id = match.id,
                                name = match.name,
                                email = match.email ?: "",
                                phone = match.phone ?: "",
                                dni = match.dni ?: "",
                                code = match.code ?: "123",
                                sidePattern = match.sidePattern ?: "LRL",
                                role = "Usuario",
                                description = desc,
                                hasDisability = match.hasDisability,
                                visualCondition = desc
                            )
                            authRepository.persistUser(profile)
                            handleAccountDetected(profile)
                            return@launch
                        }
                    }
                } catch (_: Exception) {}
                triggerVoiceError("No se encontró ninguna cuenta asociada a: \"$recognizedName\".")
            }
        }
    }

    private fun handleAccountDetected(found: UserProfile) {
        vibrationService.vibrateSuccess()
        _voiceAuthState.update {
            it.copy(
                phase = VoiceAuthPhase.ACCOUNT_DETECTED,
                detectedUser = found,
                feedbackMessage = "Cuenta detectada: ${found.name}. Escuchando PIN..."
            )
        }
        ttsService.speak("Cuenta detectada: ${found.name}. Escuchando PIN de cuatro dígitos.", onDone = {
            // Iniciar escucha del PIN
            startListeningForPin(found)
        })
    }

    /**
     * Paso 3: Ingreso y Confirmación Segura del PIN
     * Muestra los 4 puntos amarillos interactivos (● ● ● ●) de forma discreta
     */
    fun startListeningForPin(user: UserProfile) {
        _voiceAuthState.update {
            it.copy(
                phase = VoiceAuthPhase.LISTENING_PIN,
                enteredPin = "",
                feedbackMessage = "Escuchando PIN de 4 dígitos..."
            )
        }

        speechService.startListening(
            onReady = {},
            onResult = { recognizedPinText ->
                processPinInput(user, recognizedPinText)
            },
            onError = {
                // Si hubo tiempo de espera, permitir ingresar por toques o reintentar
                ttsService.speak("No se escuchó el PIN. Puede tocar la pantalla para ingresar los dígitos de forma discreta o pulsar Ayuda.")
            }
        )
    }

    private fun processPinInput(user: UserProfile, pinText: String) {
        // Extraer dígitos o palabras numéricas
        val digits = extractDigitsFromSpeech(pinText)
        if (digits.length >= 4) {
            val pinToVerify = digits.take(4)
            _voiceAuthState.update { it.copy(enteredPin = pinToVerify) }
            validatePin(user, pinToVerify)
        } else if (digits.isNotEmpty()) {
            _voiceAuthState.update { it.copy(enteredPin = digits) }
            ttsService.speak("Dígitos recibidos. Faltan ${4 - digits.length} dígitos.")
        } else {
            triggerVoiceError("No se reconocieron dígitos válidos para el PIN.")
        }
    }

    fun addPinDigit(digit: Char) {
        val currentPin = _voiceAuthState.value.enteredPin
        if (currentPin.length < 4) {
            val newPin = currentPin + digit
            vibrationService.vibrateClick()
            _voiceAuthState.update { it.copy(enteredPin = newPin) }

            if (newPin.length == 4) {
                val user = _voiceAuthState.value.detectedUser
                if (user != null) {
                    validatePin(user, newPin)
                } else {
                    triggerVoiceError("Por favor, identifícate diciendo tu nombre primero.")
                }
            }
        }
    }

    fun clearPin() {
        _voiceAuthState.update { it.copy(enteredPin = "") }
        ttsService.speak("PIN reiniciado. Ingrese los 4 dígitos.")
    }

    private fun validatePin(user: UserProfile, pin: String) {
        if (authRepository.verifyPin(user, pin)) {
            vibrationService.vibrateSuccess()
            authRepository.loginWithProfile(user)
            _voiceAuthState.update {
                it.copy(
                    phase = VoiceAuthPhase.SUCCESS,
                    feedbackMessage = "Acceso Exitoso. Bienvenido ${user.name}"
                )
            }
            _uiState.update { it.copy(authenticatedUser = user) }
            ttsService.speak("Acceso exitoso. Bienvenido ${user.name}.")
        } else {
            triggerVoiceError("PIN incorrecto para la cuenta de ${user.name}.")
        }
    }

    private fun triggerVoiceError(msg: String) {
        vibrationService.vibrateError()
        speechService.stopListening()
        _voiceAuthState.update {
            it.copy(
                phase = VoiceAuthPhase.ERROR,
                errorMessage = msg,
                feedbackMessage = msg
            )
        }
        ttsService.speak("¡Error de entrada de voz! $msg")
    }

    fun retryVoiceAuth() {
        startVoiceAuth()
    }

    fun resetVoiceAuth() {
        speechService.stopListening()
        _voiceAuthState.value = VoiceAuthState()
    }

    private fun extractDigitsFromSpeech(text: String): String {
        val lower = text.lowercase().trim()
        val directDigits = lower.filter { it.isDigit() }
        if (directDigits.isNotEmpty()) return directDigits

        // Mapeo fonético en español
        val wordMap = mapOf(
            "cero" to "0", "uno" to "1", "un" to "1", "dos" to "2", "tres" to "3",
            "cuatro" to "4", "cinco" to "5", "seis" to "6", "siete" to "7",
            "ocho" to "8", "nueve" to "9"
        )
        val sb = StringBuilder()
        lower.split(" ").forEach { word ->
            wordMap[word]?.let { sb.append(it) }
        }
        return sb.toString()
    }

    // ----------------------------------------------------
    // LOGIN MANUAL (FIGMA LOGIN-BIENVENIDA.PNG)
    // ----------------------------------------------------

    fun loginManual(
        userOrEmail: String,
        passwordOrPin: String,
        onSuccess: (UserProfile) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.loginRemote(userOrEmail, passwordOrPin)
            result.onSuccess { found ->
                vibrationService.vibrateSuccess()
                authRepository.loginWithProfile(found)
                _uiState.update { it.copy(authenticatedUser = found) }
                ttsService.speak("Bienvenido ${found.name}. Accediendo a sus mensajes.")
                onSuccess(found)
            }.onFailure { err ->
                vibrationService.vibrateError()
                val msg = if (err.message?.contains("Credenciales") == true) "Contraseña o PIN incorrecto." else "Usuario o correo no encontrado."
                onError(msg)
                ttsService.speak(msg)
            }
        }
    }

    fun registerManual(
        form: RegistrationForm,
        onSuccess: (UserProfile) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.registerRemote(form)
            result.onSuccess { newUser ->
                vibrationService.vibrateSuccess()
                authRepository.loginWithProfile(newUser)
                _uiState.update { it.copy(authenticatedUser = newUser) }
                onSuccess(newUser)
            }.onFailure { err ->
                onError(err.message ?: "Error al registrarse.")
            }
        }
    }

    // ----------------------------------------------------
    // FLUJO DE REGISTRO (FIGMA CANVAS-2.PNG)
    // ----------------------------------------------------

    fun setRegisterStep(step: RegisterStep) {
        _registerStep.value = step
        when (step) {
            RegisterStep.METHOD_SELECTION -> ttsService.speak("Crear cuenta. Elige cómo deseas registrarte en la aplicación: con correo electrónico o con número de celular.")
            RegisterStep.PERSONAL_DATA -> ttsService.speak("Paso 1 de 4: Datos personales. Ingrese su nombre completo, DNI y fecha de nacimiento.")
            RegisterStep.CONTACT_INFO -> ttsService.speak("Paso 2 de 4: Información de contacto. Ingrese su número de celular y correo electrónico.")
            RegisterStep.DISABILITY_QUESTION -> ttsService.speak("Paso 3 de 4: ¿Eres una persona con discapacidad? Selecciona si tienes discapacidad visual o no.")
            RegisterStep.VISUAL_CONDITION -> ttsService.speak("¿Qué tipo de discapacidad visual tienes? Selecciona: Ceguera total, Baja visión, u Otra condición visual.")
            RegisterStep.SUMMARY_REVIEW -> ttsService.speak("Paso 4 de 4: Revisa tu información. Antes de continuar, verifica que tus datos sean correctos.")
            RegisterStep.COMPLETED -> ttsService.speak("¡Registro completado! Tu cuenta ha sido creada correctamente. Ahora puedes iniciar sesión.")
        }
    }

    fun updateRegisterMethod(method: RegisterMethod) {
        _registrationForm.update { it.copy(method = method) }
        setRegisterStep(RegisterStep.PERSONAL_DATA)
    }

    fun updatePersonalData(name: String, dni: String, birthDate: String) {
        _registrationForm.update {
            it.copy(fullName = name, dni = dni, birthDate = birthDate)
        }
        setRegisterStep(RegisterStep.CONTACT_INFO)
    }

    fun updateContactData(phone: String, email: String) {
        _registrationForm.update {
            it.copy(phone = phone, email = email)
        }
        setRegisterStep(RegisterStep.DISABILITY_QUESTION)
    }

    fun updateDisabilitySelection(hasDisability: Boolean) {
        _registrationForm.update { it.copy(hasDisability = hasDisability) }
        if (hasDisability) {
            setRegisterStep(RegisterStep.VISUAL_CONDITION)
        } else {
            _registrationForm.update { it.copy(visualCondition = "Sin discapacidad") }
            setRegisterStep(RegisterStep.SUMMARY_REVIEW)
        }
    }

    fun updateVisualCondition(condition: String) {
        _registrationForm.update { it.copy(visualCondition = condition) }
        setRegisterStep(RegisterStep.SUMMARY_REVIEW)
    }

    fun submitRegistration(onSuccess: (UserProfile) -> Unit) {
        viewModelScope.launch {
            vibrationService.vibrateSuccess()
            val result = authRepository.registerRemote(_registrationForm.value)
            val newUser = result.getOrElse { authRepository.registerUser(_registrationForm.value) }
            _uiState.update { it.copy(authenticatedUser = newUser) }
            setRegisterStep(RegisterStep.COMPLETED)
            onSuccess(newUser)
        }
    }

    fun triggerHoverVibration() {
        vibrationService.vibrateHover()
    }

    fun triggerBothSidesSelection() {
        vibrationService.vibrateBothSidesSelection()
    }

    fun logout() {
        authRepository.logout()
        _uiState.update {
            LoginUiState(feedbackMessage = "Sesión cerrada correctamente.")
        }
        resetVoiceAuth()
        ttsService.speak("Sesión cerrada correctamente.")
    }

    override fun onCleared() {
        super.onCleared()
        speechService.destroy()
        ttsService.shutdown()
    }
}
