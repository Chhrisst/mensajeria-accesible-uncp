package com.example.mensajeria.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.ui.components.AccessibleBackButton
import com.example.mensajeria.ui.components.AccessibleOutlinedButton
import com.example.mensajeria.ui.components.PinDotIndicator
import com.example.mensajeria.ui.components.VoiceAssistantLogo
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray
import com.example.mensajeria.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla 2.2: Inicio de sesión accesible por voz con contraseña por toques laterales o PIN.
 * Solicita de inmediato el permiso nativo del micrófono y escucha sin interrupciones.
 */
@Composable
fun AccessibleVoiceLoginScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onLoginSuccess: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var detectedUser by remember { mutableStateOf<UserProfile?>(null) }
    var sidePatternEntered by remember { mutableStateOf("") }
    var isListeningName by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("Iniciando reconocimiento por voz...") }

    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) {
            authViewModel.soundService.playSuccessSound()
            isListeningName = true
            sidePatternEntered = ""
            feedbackText = "Permiso concedido. Diga su nombre de usuario..."

            authViewModel.ttsService.speak(
                "Permiso concedido. Di tu nombre de usuario en voz alta.",
                onDone = {
                    coroutineScope.launch {
                        delay(450)
                        authViewModel.startVoiceAuth()
                    }
                }
            )
        } else {
            feedbackText = "Permiso de micrófono no concedido. Pulse Conceder Permiso para habilitarlo."
            authViewModel.ttsService.speak(
                "El permiso de micrófono no fue concedido. Para iniciar sesión por voz, debes permitir el acceso al micrófono."
            )
        }
    }

    fun startListeningForName() {
        if (!hasRecordPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        isListeningName = true
        sidePatternEntered = ""
        feedbackText = "Diga su nombre de usuario en voz alta..."
        authViewModel.ttsService.speak(
            "Para iniciar sesión, por favor di tu nombre de usuario en voz alta.",
            onDone = {
                coroutineScope.launch {
                    delay(450)
                    authViewModel.startVoiceAuth()
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        if (!hasRecordPermission) {
            authViewModel.ttsService.speak("Por favor, concede el permiso de micrófono en el mensaje de la pantalla.")
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListeningForName()
        }
    }

    // Observar estado de usuario detectado por AuthViewModel
    val voiceAuthState by authViewModel.voiceAuthState.collectAsState()
    LaunchedEffect(voiceAuthState.detectedUser) {
        if (voiceAuthState.detectedUser != null && detectedUser == null) {
            detectedUser = voiceAuthState.detectedUser
            isListeningName = false
            feedbackText = "Cuenta detectada: ${detectedUser?.name}."
            authViewModel.ttsService.speak(
                "Cuenta detectada: ${detectedUser?.name}. Ahora ingresa tu clave secreta de cuatro toques tocando los lados de la pantalla de forma discreta."
            )
        }
    }

    fun handleSideTap(side: Char) {
        if (detectedUser == null) return

        // Retroalimentación háptica exclusiva y diferenciada por lado:
        // Izquierda: 1 pulso (35ms)
        // Derecha: 2 pulsos (doble vibración)
        if (side == 'L') {
            authViewModel.vibrationService.vibrateLeftTap()
        } else {
            authViewModel.vibrationService.vibrateRightTap()
        }
        // Seguridad y discreción:
        // NO se vocaliza "Izquierda" ni "Derecha" por TTS.
        // NO se emiten tonos audibles que delaten los lados pulsados.

        val newPattern = sidePatternEntered + side
        sidePatternEntered = newPattern

        if (newPattern.length == 4) {
            // Validar patrón contra el usuario
            val isValid = authViewModel.authRepository.verifyPin(detectedUser!!, newPattern)
            if (isValid) {
                authViewModel.soundService.playSuccessSound()
                authViewModel.vibrationService.vibrateSuccess()
                authViewModel.ttsService.speak("Contraseña correcta. Acceso exitoso. Bienvenido ${detectedUser?.name}.")
                authViewModel.authRepository.loginWithProfile(detectedUser!!)
                onLoginSuccess(detectedUser!!)
            } else {
                authViewModel.vibrationService.vibrateError()
                authViewModel.ttsService.speak("Contraseña incorrecta. Intenta nuevamente tocando los cuatro toques.")
                sidePatternEntered = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Barra superior de navegación
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AccessibleBackButton(onClick = onBack)
                Text(
                    text = "Inicio de Sesión por Voz",
                    color = AccessibleYellow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (detectedUser == null) {
                // FASE 1: RECONOCIMIENTO DE NOMBRE DE USUARIO
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    VoiceAssistantLogo(
                        size = 180.dp,
                        isPulsing = isListeningName,
                        onClick = {
                            if (!hasRecordPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                startListeningForName()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Di tu nombre de cuenta",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccessibleYellow,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = feedbackText,
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    AccessibleOutlinedButton(
                        text = if (!hasRecordPermission) {
                            "Conceder Permiso de Micrófono"
                        } else if (isListeningName) {
                            "Escuchando tu voz..."
                        } else {
                            "Reintentar Reconocimiento"
                        },
                        onClick = {
                            if (!hasRecordPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                startListeningForName()
                            }
                        },
                        leadingIcon = Icons.Default.Mic
                    )
                }
            } else {
                // FASE 2: CONTRASEÑA POR TOQUES LATERALES (IZQUIERDA / DERECHA)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Cuenta: ${detectedUser?.name}",
                        fontSize = 18.sp,
                        color = TextSecondaryGray
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Ingresa tu clave de 4 toques",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccessibleYellow,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Pulsaciones ocultas por seguridad (solo vibración)",
                        fontSize = 12.sp,
                        color = Color(0xFF8E8E93),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Indicador de 4 puntos interactivos protegidos
                    PinDotIndicator(
                        enteredLength = sidePatternEntered.length,
                        maxDigits = 4
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // DOS PANELES LATERALES GIGANTES TÁCTILES: IZQUIERDA Y DERECHA
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // LADO IZQUIERDO (L)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { handleSideTap('L') }
                                .semantics {
                                    this.role = Role.Button
                                    this.contentDescription = "Toque Lado Izquierdo."
                                },
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardBackground),
                            border = BorderStroke(2.5.dp, AccessibleYellow)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "LADO\nIZQUIERDO",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccessibleYellow,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Pulsa aquí",
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                        }

                        // LADO DERECHO (R)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { handleSideTap('R') }
                                .semantics {
                                    this.role = Role.Button
                                    this.contentDescription = "Toque Lado Derecho."
                                },
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardBackground),
                            border = BorderStroke(2.5.dp, AccessibleYellow)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "LADO\nDERECHO",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccessibleYellow,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Pulsa aquí",
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AccessibleOutlinedButton(
                            text = "Borrar clave",
                            onClick = {
                                sidePatternEntered = ""
                                authViewModel.soundService.playButtonClickSound()
                                authViewModel.ttsService.speak("Clave reiniciada. Vuelve a ingresar tus cuatro toques.")
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.size(12.dp))

                        AccessibleOutlinedButton(
                            text = "Cambiar cuenta",
                            onClick = {
                                detectedUser = null
                                startListeningForName()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
