package com.example.mensajeria

import android.Manifest
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
import androidx.core.content.ContextCompat
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.data.repository.AuthRepository
import com.example.mensajeria.data.repository.ChatRepository
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
    private val authRepository by lazy { AuthRepository(applicationContext) }
    private val chatRepository by lazy { ChatRepository(applicationContext) }

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

        authViewModel = AuthViewModel(applicationContext, authRepository)
        chatViewModel = ChatViewModel(applicationContext, chatRepository)
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
    onRequestPermission: (() -> Unit) -> Unit
) {
    var currentScreen by remember {
        mutableStateOf(AppFlowScreen.INITIAL_WELCOME)
    }

    var isDisabilityMode by remember { mutableStateOf(true) }
    val contacts by chatRepository.contacts.collectAsState()
    var selectedContact by remember { mutableStateOf(contacts.firstOrNull() ?: Contact("22222222-2222-2222-2222-222222222222", "Romell Rivera", description = "Con discapacidad")) }
    val authState by authViewModel.uiState.collectAsState()

    val currentUser = authState.authenticatedUser ?: authViewModel.authRepository.predefinedUsers.first()

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
        voiceAssistantManager.getCurrentUserId = { currentUser.id }
        voiceAssistantManager.getActiveChatContact = {
            if (currentScreen == AppFlowScreen.ACCESSIBLE_CHAT) selectedContact else null
        }

        voiceAssistantManager.stopAssistant()
    }

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

        // 1. PREGUNTA DE DISCAPACIDAD ("¿Cómo deseas usar la app?")
        AppFlowScreen.DISABILITY_SELECTION -> {
            BackHandler {
                currentScreen = AppFlowScreen.INITIAL_WELCOME
            }
            AccessibilityModeSelectionScreen(
                onSelectMode = { hasDisability ->
                    isDisabilityMode = hasDisability
                    chatViewModel.isDisabilityMode = hasDisability
                    if (hasDisability) {
                        currentScreen = AppFlowScreen.ACCESSIBLE_WELCOME
                    } else {
                        currentScreen = AppFlowScreen.STANDARD_LOGIN
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
                onLoginSuccess = { user ->
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
                onRegistrationSuccess = { user ->
                    currentScreen = AppFlowScreen.ACCESSIBLE_CONTACTS
                }
            )
        }

        // 2.4 LISTA DE CONTACTOS CON NAVEGACIÓN HÁPTICA Y SELECCIÓN POR LADOS
        AppFlowScreen.ACCESSIBLE_CONTACTS -> {
            BackHandler {
                authViewModel.logout()
                currentScreen = AppFlowScreen.ACCESSIBLE_WELCOME
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
                onLoginSuccess = { user ->
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
                onRegistrationSuccess = { user ->
                    currentScreen = AppFlowScreen.STANDARD_CONTACTS
                }
            )
        }

        // 3.1 LISTA DE CONTACTOS ESTÁNDAR
        AppFlowScreen.STANDARD_CONTACTS -> {
            BackHandler {
                authViewModel.logout()
                currentScreen = AppFlowScreen.STANDARD_LOGIN
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
}
