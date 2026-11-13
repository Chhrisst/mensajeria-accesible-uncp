package com.example.mensajeria

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.data.repository.AuthRepository
import com.example.mensajeria.data.repository.ChatRepository
import com.example.mensajeria.ui.components.AccessibleTouchExplorationHost
import com.example.mensajeria.ui.components.VoiceCallOverlay
import com.google.gson.Gson
import com.example.mensajeria.ui.screens.AccessibilityModeSelectionScreen
import com.example.mensajeria.ui.screens.AccessibleChatScreen
import com.example.mensajeria.ui.screens.AccessibleContactListScreen
import com.example.mensajeria.ui.screens.AccessibleVoiceLoginScreen
import com.example.mensajeria.ui.screens.AccessibleVoiceRegisterScreen
import com.example.mensajeria.ui.screens.AccessibleWelcomeScreen
import com.example.mensajeria.ui.screens.PermissionScreen
import com.example.mensajeria.ui.screens.StandardChatScreen
import com.example.mensajeria.ui.screens.StandardContactListScreen
import com.example.mensajeria.ui.screens.StandardLoginScreen
import com.example.mensajeria.ui.screens.StandardRegisterScreen
import com.example.mensajeria.ui.screens.WelcomeScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.MensajeriaTheme
import com.example.mensajeria.ui.viewmodel.AuthViewModel
import com.example.mensajeria.ui.viewmodel.ChatViewModel
import com.example.mensajeria.domain.service.VoiceAssistantManager

enum class AppFlowScreen {
    PERMISSION_CHECK,
    INITIAL_WELCOME,
    DISABILITY_SELECTION,

    // MODO CON DISCAPACIDAD VISUAL (ASISTIDO)
    ACCESSIBLE_WELCOME,
    ACCESSIBLE_VOICE_LOGIN,
    ACCESSIBLE_VOICE_REG,
    ACCESSIBLE_CONTACTS,
    ACCESSIBLE_CHAT,

    // MODO SIN DISCAPACIDAD (ESTÁNDAR TIPO WHATSAPP)
    STANDARD_LOGIN,
    STANDARD_REGISTER,
    STANDARD_CONTACTS,
    STANDARD_CHAT
}

class MainActivity : ComponentActivity() {

    private lateinit var authViewModel: AuthViewModel
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var voiceAssistantManager: VoiceAssistantManager
    // El repositorio se obtiene del ViewModel para que sobreviva junto a él a la recreación
    // de la Activity (rotación, cambio de tamaño de fuente o de tema).
    private val chatRepository: ChatRepository get() = chatViewModel.repository

    /**
     * Antes los ViewModels se instanciaban con su constructor: en cada recreación de la Activity se creaba
     * un par nuevo y el anterior nunca recibía onCleared(), dejando vivos su motor TTS, su SpeechRecognizer
     * y el bucle de sondeo de mensajes (uno más por cada giro de pantalla).
     */
    private val viewModelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(applicationContext, AuthRepository(applicationContext)) as T
            modelClass.isAssignableFrom(ChatViewModel::class.java) ->
                ChatViewModel(applicationContext, ChatRepository(applicationContext)) as T
            else -> throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
        }
    }

    private var onPermissionGrantedCallback: (() -> Unit)? = null

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGrantedCallback?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val provider = ViewModelProvider(this, viewModelFactory)
        authViewModel = provider[AuthViewModel::class.java]
        chatViewModel = provider[ChatViewModel::class.java]
        voiceAssistantManager = VoiceAssistantManager(
            context = applicationContext,
            chatRepository = chatRepository,
            speechService = authViewModel.speechService,
            ttsService = authViewModel.ttsService,
            vibrationService = authViewModel.vibrationService,
            soundService = authViewModel.soundService
        )

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            MensajeriaTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                        .systemBarsPadding()
                ) {
                    MensajeriaApp(
                        initialHasPermission = hasAudioPermission,
                        authViewModel = authViewModel,
                        chatViewModel = chatViewModel,
                        voiceAssistantManager = voiceAssistantManager,
                        chatRepository = chatRepository,
                        onRequestPermission = { callback ->
                            onPermissionGrantedCallback = callback
                            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onMinimizeApp = {
                            moveTaskToBack(true)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceAssistantManager.destroy()
    }
}

@Composable
fun MensajeriaApp(
    initialHasPermission: Boolean,
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    voiceAssistantManager: VoiceAssistantManager,
    chatRepository: ChatRepository,
    onRequestPermission: (() -> Unit) -> Unit,
    onMinimizeApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val navPrefs = remember { context.getSharedPreferences("uncp_nav_state_prefs", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    val authState by authViewModel.uiState.collectAsState()
    val chatUiState by chatViewModel.uiState.collectAsState()
    val savedSessionUser = authState.authenticatedUser ?: authViewModel.authRepository.currentUser.collectAsState().value

    var isDisabilityMode by remember {
        mutableStateOf(savedSessionUser?.hasDisability ?: navPrefs.getBoolean("is_disability_mode", true))
    }

    val contacts by chatRepository.contacts.collectAsState()

    var selectedContact by remember {
        val savedContactJson = navPrefs.getString("selected_contact_json", null)
        val restoredContact = try {
            if (!savedContactJson.isNullOrBlank()) gson.fromJson(savedContactJson, Contact::class.java) else null
        } catch (_: Exception) { null }
        mutableStateOf(
            restoredContact ?: contacts.firstOrNull() ?: Contact(id = "", name = "", username = "", description = "")
        )
    }

    var currentScreen by remember {
        val initialScreen = if (savedSessionUser != null && savedSessionUser.id.isNotBlank()) {
            val savedScreenName = navPrefs.getString("last_auth_screen", null)
            val restoredScreen = try {
                if (savedScreenName != null) AppFlowScreen.valueOf(savedScreenName) else null
            } catch (_: Exception) { null }

            when {
                restoredScreen == AppFlowScreen.ACCESSIBLE_CHAT && selectedContact.id.isNotBlank() -> AppFlowScreen.ACCESSIBLE_CHAT
                restoredScreen == AppFlowScreen.STANDARD_CHAT && selectedContact.id.isNotBlank() -> AppFlowScreen.STANDARD_CHAT
                savedSessionUser.hasDisability -> AppFlowScreen.ACCESSIBLE_CONTACTS
                else -> AppFlowScreen.STANDARD_CONTACTS
            }
        } else {
            if (initialHasPermission) AppFlowScreen.INITIAL_WELCOME else AppFlowScreen.PERMISSION_CHECK
        }
        mutableStateOf(initialScreen)
    }

    val currentUser = savedSessionUser ?: UserProfile(id = "", name = "Usuario", username = "")

    // Persistir estado de navegación de la sesión activa para no perderla en segundo plano
    androidx.compose.runtime.LaunchedEffect(currentScreen, selectedContact, currentUser, isDisabilityMode) {
        navPrefs.edit().putBoolean("is_disability_mode", isDisabilityMode).apply()
        if (currentUser.id.isNotBlank()) {
            chatViewModel.isDisabilityMode = currentUser.hasDisability
            chatViewModel.bindUserSession(currentUser.id, currentUser.name)
            if (currentScreen == AppFlowScreen.ACCESSIBLE_CONTACTS ||
                currentScreen == AppFlowScreen.ACCESSIBLE_CHAT ||
                currentScreen == AppFlowScreen.STANDARD_CONTACTS ||
                currentScreen == AppFlowScreen.STANDARD_CHAT
            ) {
                navPrefs.edit()
                    .putString("last_auth_screen", currentScreen.name)
                    .putString("selected_contact_json", gson.toJson(selectedContact))
                    .apply()
            }
            if ((currentScreen == AppFlowScreen.ACCESSIBLE_CHAT || currentScreen == AppFlowScreen.STANDARD_CHAT) && selectedContact.id.isNotBlank()) {
                chatViewModel.setContact(selectedContact, currentUser.id)
            }
        } else {
            chatViewModel.bindUserSession("", "")
            navPrefs.edit()
                .remove("last_auth_screen")
                .remove("selected_contact_json")
                .apply()
        }
    }

    // Sincronizar callbacks del Asistente de Voz Inteligente con el estado actual
    androidx.compose.runtime.LaunchedEffect(currentScreen, selectedContact, currentUser) {
        voiceAssistantManager.onOpenChat = { contact ->
            selectedContact = contact
            chatViewModel.setContact(contact, currentUser.id)
            currentScreen = AppFlowScreen.ACCESSIBLE_CHAT
        }
        voiceAssistantManager.onBackToContacts = {
            chatViewModel.stopPolling()
            currentScreen = AppFlowScreen.ACCESSIBLE_CONTACTS
        }
        voiceAssistantManager.onLogout = {
            chatViewModel.stopPolling()
            authViewModel.logout()
            currentScreen = AppFlowScreen.ACCESSIBLE_WELCOME
        }
        voiceAssistantManager.getCurrentScreen = {
            when (currentScreen) {
                AppFlowScreen.ACCESSIBLE_CHAT -> "CHAT"
                AppFlowScreen.ACCESSIBLE_CONTACTS -> "CONTACTS"
                else -> "OTHER"
            }
        }
        voiceAssistantManager.getCurrentUser = { currentUser.name }
        voiceAssistantManager.getCurrentUsername = { currentUser.username }
        voiceAssistantManager.getCurrentUserId = { currentUser.id }
        voiceAssistantManager.getActiveChatContact = {
            if (currentScreen == AppFlowScreen.ACCESSIBLE_CHAT) selectedContact else null
        }
        voiceAssistantManager.onSummarizeLastMessage = {
            chatViewModel.summarizeLastMessage()
        }
        voiceAssistantManager.onStartCall = {
            chatViewModel.startVoiceCall()
        }
        voiceAssistantManager.onStartVoiceNote = {
            chatViewModel.startVoiceNoteRecording()
        }

        voiceAssistantManager.stopAssistant()
    }

    val isExploreEnabled = when (currentScreen) {
        AppFlowScreen.STANDARD_LOGIN,
        AppFlowScreen.STANDARD_REGISTER,
        AppFlowScreen.STANDARD_CONTACTS,
        AppFlowScreen.STANDARD_CHAT -> false
        else -> true
    }

    AccessibleTouchExplorationHost(
        enabled = isExploreEnabled,
        screenKey = currentScreen,
        onExploreHover = { shortLabel ->
            authViewModel.triggerHoverVibration()
            authViewModel.ttsService.speak(shortLabel)
        }
    ) {
    when (currentScreen) {
        // 0. PERMISOS
        AppFlowScreen.PERMISSION_CHECK -> {
            PermissionScreen(
                onRequestPermission = {
                    onRequestPermission {
                        currentScreen = AppFlowScreen.INITIAL_WELCOME
                    }
                },
                onSpeakAnnouncement = { text ->
                    authViewModel.ttsService.speak(text)
                }
            )
        }

        // 0.1 PANTALLA INICIAL DE BIENVENIDA CON NARRADOR "Bienvenido usuario"
        AppFlowScreen.INITIAL_WELCOME -> {
            WelcomeScreen(
                onStartClick = {
                    currentScreen = AppFlowScreen.DISABILITY_SELECTION
                },
                onSpeakAnnouncement = { text ->
                    authViewModel.ttsService.speak(text)
                }
            )
        }

        // 1. Pregunta sobre modo accesible o estándar
        AppFlowScreen.DISABILITY_SELECTION -> {
            BackHandler {
                currentScreen = AppFlowScreen.INITIAL_WELCOME
            }
            AccessibilityModeSelectionScreen(
                onSelectMode = { hasDisability ->
                    isDisabilityMode = hasDisability
                    chatViewModel.isDisabilityMode = hasDisability
                    currentScreen = if (hasDisability) {
                        AppFlowScreen.ACCESSIBLE_WELCOME
                    } else {
                        AppFlowScreen.STANDARD_LOGIN
                    }
                },
                onSpeakAnnouncement = { text ->
                    authViewModel.ttsService.speak(text)
                }
            )
        }

        // 2.1 PANTALLA DE BIENVENIDA ACCESIBLE (IZQUIERDA: LOGIN / DERECHA: REGISTRO)
        AppFlowScreen.ACCESSIBLE_WELCOME -> {
            BackHandler {
                currentScreen = AppFlowScreen.DISABILITY_SELECTION
            }
            AccessibleWelcomeScreen(
                onLoginClick = {
                    currentScreen = AppFlowScreen.ACCESSIBLE_VOICE_LOGIN
                },
                onRegisterClick = {
                    currentScreen = AppFlowScreen.ACCESSIBLE_VOICE_REG
                },
                onChangeModeClick = {
                    currentScreen = AppFlowScreen.DISABILITY_SELECTION
                },
                onSpeakAnnouncement = { text ->
                    authViewModel.ttsService.speak(text)
                }
            )
        }

        // 2.2 INICIO DE SESIÓN ACCESIBLE POR VOZ Y TOQUES LATERALES
        AppFlowScreen.ACCESSIBLE_VOICE_LOGIN -> {
            BackHandler {
                currentScreen = AppFlowScreen.ACCESSIBLE_WELCOME
            }
            AccessibleVoiceLoginScreen(
                authViewModel = authViewModel,
                onBack = { currentScreen = AppFlowScreen.ACCESSIBLE_WELCOME },
                onLoginSuccess = { _ ->
                    currentScreen = AppFlowScreen.ACCESSIBLE_CONTACTS
                }
            )
        }

        // 2.3 REGISTRO ACCESIBLE POR VOZ Y TOQUES LATERALES
        AppFlowScreen.ACCESSIBLE_VOICE_REG -> {
            BackHandler {
                currentScreen = AppFlowScreen.ACCESSIBLE_WELCOME
            }
            AccessibleVoiceRegisterScreen(
                authViewModel = authViewModel,
                onBack = { currentScreen = AppFlowScreen.ACCESSIBLE_WELCOME },
                onRegistrationSuccess = { _ ->
                    currentScreen = AppFlowScreen.ACCESSIBLE_CONTACTS
                }
            )
        }

        // 2.4 LISTA DE CONTACTOS CON NAVEGACIÓN HÁPTICA Y SELECCIÓN POR LADOS
        AppFlowScreen.ACCESSIBLE_CONTACTS -> {
            BackHandler {
                onMinimizeApp()
            }
            androidx.compose.runtime.LaunchedEffect(currentUser.id) {
                chatRepository.setSessionUser(currentUser.id)
                chatRepository.syncContactsFromRemote(currentUser.id)
            }
            AccessibleContactListScreen(
                currentUser = currentUser,
                contacts = contacts,
                authViewModel = authViewModel,
                voiceAssistantManager = voiceAssistantManager,
                onSelectContact = { contact ->
                    selectedContact = contact
                    chatViewModel.setContact(contact, currentUser.id)
                    currentScreen = AppFlowScreen.ACCESSIBLE_CHAT
                },
                onLogout = {
                    authViewModel.logout()
                    currentScreen = AppFlowScreen.ACCESSIBLE_WELCOME
                }
            )
        }

        // 2.5 CHAT ACCESIBLE CON VOZ A TEXTO, NOTA DE VOZ, SALIR EN ESQUINA SUPERIOR IZQUIERDA
        AppFlowScreen.ACCESSIBLE_CHAT -> {
            BackHandler {
                chatViewModel.stopPolling()
                currentScreen = AppFlowScreen.ACCESSIBLE_CONTACTS
            }
            AccessibleChatScreen(
                contact = selectedContact,
                chatViewModel = chatViewModel,
                voiceAssistantManager = voiceAssistantManager,
                onBackToContacts = {
                    chatViewModel.stopPolling()
                    currentScreen = AppFlowScreen.ACCESSIBLE_CONTACTS
                }
            )
        }

        // 3.1 LOGIN ESTÁNDAR (TIPO WHATSAPP)
        AppFlowScreen.STANDARD_LOGIN -> {
            BackHandler {
                currentScreen = AppFlowScreen.DISABILITY_SELECTION
            }
            StandardLoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = { _ ->
                    currentScreen = AppFlowScreen.STANDARD_CONTACTS
                },
                onGoToRegister = {
                    currentScreen = AppFlowScreen.STANDARD_REGISTER
                },
                onChangeMode = {
                    currentScreen = AppFlowScreen.DISABILITY_SELECTION
                }
            )
        }

        // 3.1 REGISTRO ESTÁNDAR
        AppFlowScreen.STANDARD_REGISTER -> {
            BackHandler {
                currentScreen = AppFlowScreen.STANDARD_LOGIN
            }
            StandardRegisterScreen(
                authViewModel = authViewModel,
                onBackToLogin = {
                    currentScreen = AppFlowScreen.STANDARD_LOGIN
                },
                onRegistrationSuccess = { _ ->
                    currentScreen = AppFlowScreen.STANDARD_CONTACTS
                }
            )
        }

        // 3.1 LISTA DE CONTACTOS ESTÁNDAR
        AppFlowScreen.STANDARD_CONTACTS -> {
            BackHandler {
                onMinimizeApp()
            }
            androidx.compose.runtime.LaunchedEffect(currentUser.id) {
                chatRepository.setSessionUser(currentUser.id)
                chatRepository.syncContactsFromRemote(currentUser.id)
            }
            StandardContactListScreen(
                currentUser = currentUser,
                contacts = contacts,
                chatRepository = chatRepository,
                onSelectContact = { contact ->
                    selectedContact = contact
                    chatViewModel.setContact(contact, currentUser.id)
                    currentScreen = AppFlowScreen.STANDARD_CHAT
                },
                onLogout = {
                    authViewModel.logout()
                    currentScreen = AppFlowScreen.STANDARD_LOGIN
                }
            )
        }

        // 3.1 CHAT ESTÁNDAR (TIPO WHATSAPP)
        AppFlowScreen.STANDARD_CHAT -> {
            BackHandler {
                chatViewModel.stopPolling()
                currentScreen = AppFlowScreen.STANDARD_CONTACTS
            }
            StandardChatScreen(
                contact = selectedContact,
                chatViewModel = chatViewModel,
                onBackToContacts = {
                    chatViewModel.stopPolling()
                    currentScreen = AppFlowScreen.STANDARD_CONTACTS
                }
            )
        }
    }
    if (chatUiState.isInCall &&
        currentScreen != AppFlowScreen.ACCESSIBLE_CHAT &&
        currentScreen != AppFlowScreen.STANDARD_CHAT
    ) {
        VoiceCallOverlay(
            contactName = chatUiState.callPartner?.name?.ifBlank { "Contacto" } ?: "Contacto",
            statusText = chatUiState.callStatusText,
            durationSeconds = chatUiState.callDurationSeconds,
            isMuted = chatUiState.isCallMuted,
            isSpeakerphoneOn = chatUiState.isSpeakerphoneOn,
            isAccessibleMode = isDisabilityMode,
            isIncomingRinging = chatUiState.isIncomingCall,
            onAcceptCall = { chatViewModel.acceptIncomingCall() },
            onRejectCall = { chatViewModel.rejectIncomingCall() },
            onToggleMute = { chatViewModel.toggleCallMute() },
            onToggleSpeaker = { chatViewModel.toggleSpeakerphone() },
            onEndCall = { chatViewModel.endVoiceCall() }
        )
    }
    }
}
