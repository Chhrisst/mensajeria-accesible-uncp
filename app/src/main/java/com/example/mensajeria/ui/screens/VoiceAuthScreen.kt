package com.example.mensajeria.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.data.model.VoiceAuthPhase
import com.example.mensajeria.ui.components.AccessibleOutlinedButton
import com.example.mensajeria.ui.components.AccessiblePrimaryButton
import com.example.mensajeria.ui.components.PinDotIndicator
import com.example.mensajeria.ui.components.VoiceAssistantLogo
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.MensajeriaTheme
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray
import com.example.mensajeria.ui.viewmodel.AuthViewModel

/**
 * Pantalla orquestadora del Flujo de Autenticación por Voz y PIN:
 * Soporta fases: Escucha activa, Cuenta detectada, Ingreso de PIN, Error y Acceso exitoso.
 */
@Composable
fun VoiceAuthScreen(
    authViewModel: AuthViewModel,
    onManualLoginClick: () -> Unit,
    onSuccessGoToInbox: (UserProfile) -> Unit
) {
    val state by authViewModel.voiceAuthState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(24.dp)
    ) {
        when (state.phase) {
            VoiceAuthPhase.IDLE, VoiceAuthPhase.LISTENING_NAME -> {
                VoiceActiveScreen(
                    onLogoClick = { authViewModel.startVoiceAuth() }
                )
            }
            VoiceAuthPhase.ACCOUNT_DETECTED -> {
                VoiceAccountDetectedScreen(
                    user = state.detectedUser ?: authViewModel.authRepository.predefinedUsers.first()
                )
            }
            VoiceAuthPhase.LISTENING_PIN -> {
                VoicePinScreen(
                    user = state.detectedUser ?: authViewModel.authRepository.predefinedUsers.first(),
                    enteredPin = state.enteredPin,
                    onDigitClick = { authViewModel.addPinDigit(it) },
                    onRepeatClick = { authViewModel.clearPin() },
                    onHelpClick = {
                        authViewModel.speakText("Ingrese su PIN de tres dígitos diciendo los números o tocando los botones en pantalla.")
                    }
                )
            }
            VoiceAuthPhase.ERROR -> {
                VoiceErrorScreen(
                    errorMessage = state.errorMessage,
                    onRetryClick = { authViewModel.retryVoiceAuth() },
                    onManualClick = onManualLoginClick,
                    onBackClick = {
                        authViewModel.resetVoiceAuth()
                        onManualLoginClick()
                    }
                )
            }
            VoiceAuthPhase.SUCCESS -> {
                VoiceSuccessScreen(
                    user = state.detectedUser ?: authViewModel.authRepository.predefinedUsers.first(),
                    onGoToInbox = {
                        state.detectedUser?.let { onSuccessGoToInbox(it) }
                    }
                )
            }
        }
    }
}

// Alias para compatibilidad
@Composable
fun FigmaVoiceAuthContainer(
    authViewModel: AuthViewModel,
    onManualLoginClick: () -> Unit,
    onSuccessGoToInbox: (UserProfile) -> Unit
) = VoiceAuthScreen(authViewModel, onManualLoginClick, onSuccessGoToInbox)

/**
 * 1. Pantalla de Bienvenida y Activación de Voz.
 */
@Composable
fun VoiceActiveScreen(
    onLogoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        VoiceAssistantLogo(
            size = 200.dp,
            isPulsing = true,
            onClick = onLogoClick
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Asistente de Voz\nActivo",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AccessibleYellow,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Diga el nombre de su cuenta para iniciar",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 2. Selección de Cuenta por Voz (Modo Seguro).
 */
@Composable
fun VoiceAccountDetectedScreen(
    user: UserProfile
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBackground),
            border = BorderStroke(2.dp, AccessibleYellow),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = user.name,
                    color = AccessibleYellow,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = user.role,
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "Cuenta detectada.",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Escuchando PIN...",
            color = TextSecondaryGray,
            fontSize = 16.sp
        )
    }
}

/**
 * 3. Ingreso y Confirmación Segura del PIN.
 */
@Composable
fun VoicePinScreen(
    user: UserProfile,
    enteredPin: String,
    onDigitClick: (Char) -> Unit,
    onRepeatClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 60.dp)
        ) {
            Text(
                text = "Cuenta: ${user.name}",
                color = TextSecondaryGray,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ingrese su PIN de 3 dígitos",
                color = AccessibleYellow,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(48.dp))

            PinDotIndicator(enteredLength = enteredPin.length, maxDigits = 3)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Toque para repetir",
                color = TextSecondaryGray,
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable(onClick = onRepeatClick)
                    .padding(8.dp)
                    .semantics {
                        this.role = Role.Button
                        this.contentDescription = "Borrar y volver a dictar o ingresar el PIN"
                    }
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf('1', '2', '3').forEach { digit ->
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(DarkCardBackground)
                            .border(1.5.dp, AccessibleYellow, CircleShape)
                            .clickable { onDigitClick(digit) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$digit",
                            color = AccessibleYellow,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            AccessiblePrimaryButton(
                text = "Ayuda",
                onClick = onHelpClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 4. Error de Entrada de Voz y Reintentos.
 */
@Composable
fun VoiceErrorScreen(
    errorMessage: String,
    onRetryClick: () -> Unit,
    onManualClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 60.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .border(2.5.dp, AccessibleYellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AccessibleYellow,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "¡Error de Entrada de\nVoz!",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = errorMessage.ifBlank { "No se pudo reconocer el comando. Por favor, intente nuevamente." },
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AccessiblePrimaryButton(
                text = "REINTENTAR DICTADO",
                onClick = onRetryClick,
                leadingIcon = Icons.Default.Mic
            )

            AccessibleOutlinedButton(
                text = "TECLADO MANUAL",
                onClick = onManualClick
            )

            AccessibleOutlinedButton(
                text = "VOLVER",
                onClick = onBackClick
            )
        }
    }
}

/**
 * 5. Acceso Exitoso.
 */
@Composable
fun VoiceSuccessScreen(
    user: UserProfile,
    onGoToInbox: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 90.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .border(2.5.dp, AccessibleYellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = AccessibleYellow,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Acceso Exitoso",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Bienvenido ${user.name}",
                fontSize = 19.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        AccessiblePrimaryButton(
            text = "Ir a Bandeja de Mensajes con IA",
            onClick = onGoToInbox,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(name = "1. Asistente de Voz Activo", showBackground = true, showSystemUi = true)
@Composable
fun VoiceActiveScreenPreview() {
    MensajeriaTheme {
        VoiceActiveScreen(
            onLogoClick = {}
        )
    }
}

@Preview(name = "2. Cuenta Detectada", showBackground = true, showSystemUi = true)
@Composable
fun VoiceAccountDetectedScreenPreview() {
    MensajeriaTheme {
        VoiceAccountDetectedScreen(
            user = UserProfile(id = "1", name = "Romell Rivera", role = "Socio Activo UNCP")
        )
    }
}

@Preview(name = "3. Ingreso de PIN", showBackground = true, showSystemUi = true)
@Composable
fun VoicePinScreenPreview() {
    MensajeriaTheme {
        VoicePinScreen(
            user = UserProfile(id = "1", name = "Romell Rivera", role = "Socio Activo UNCP"),
            enteredPin = "12",
            onDigitClick = {},
            onRepeatClick = {},
            onHelpClick = {}
        )
    }
}

@Preview(name = "4. Error de Voz", showBackground = true, showSystemUi = true)
@Composable
fun VoiceErrorScreenPreview() {
    MensajeriaTheme {
        VoiceErrorScreen(
            errorMessage = "No se pudo reconocer el comando. Intenta nuevamente.",
            onRetryClick = {},
            onManualClick = {},
            onBackClick = {}
        )
    }
}

@Preview(name = "5. Acceso Exitoso", showBackground = true, showSystemUi = true)
@Composable
fun VoiceSuccessScreenPreview() {
    MensajeriaTheme {
        VoiceSuccessScreen(
            user = UserProfile(id = "1", name = "Romell Rivera", role = "Socio Activo UNCP"),
            onGoToInbox = {}
        )
    }
}
