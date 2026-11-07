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
    private val authRepository by lazy { AuthRepository(applicationContext) }
    private val chatRepository = ChatRepository()

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
                        contacts = chatRepository.contacts.value,
                        onRequestPermission = { callback ->
                            onPermissionGrantedCallback = callback
                            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MensajeriaApp(
    initialHasPermission: Boolean,
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    contacts: List<Contact>,
    onRequestPermission: (() -> Unit) -> Unit
) {
    var currentScreen by remember {
        mutableStateOf(AppFlowScreen.INITIAL_WELCOME)
    }

    var isDisabilityMode by remember { mutableStateOf(true) }
    var selectedContact by remember { mutableStateOf(contacts.firstOrNull() ?: Contact("c1", "Lic. Ana Ramos")) }
    val authState by authViewModel.uiState.collectAsState()

    val currentUser = authState.authenticatedUser ?: authViewModel.authRepository.predefinedUsers.first()

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
            AccessibleContactListScreen(
                currentUser = currentUser,
                contacts = contacts,
                authViewModel = authViewModel,
                onSelectContact = { contact ->
                    selectedContact = contact
                    chatViewModel.setContact(contact)
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
                currentScreen = AppFlowScreen.ACCESSIBLE_CONTACTS
            }
            AccessibleChatScreen(
                contact = selectedContact,
                chatViewModel = chatViewModel,
                onBackToContacts = {
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
            StandardContactListScreen(
                currentUser = currentUser,
                contacts = contacts,
                onSelectContact = { contact ->
                    selectedContact = contact
                    chatViewModel.setContact(contact)
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
                currentScreen = AppFlowScreen.STANDARD_CONTACTS
            }
            StandardChatScreen(
                contact = selectedContact,
                chatViewModel = chatViewModel,
                onBackToContacts = {
                    currentScreen = AppFlowScreen.STANDARD_CONTACTS
                }
            )
        }
    }
}
