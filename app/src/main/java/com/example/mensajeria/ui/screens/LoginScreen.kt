package com.example.mensajeria.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.ui.components.AccessiblePrimaryButton
import com.example.mensajeria.ui.components.AccessibleTextField
import com.example.mensajeria.ui.components.VoiceAssistantLogo
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.ErrorRed
import com.example.mensajeria.ui.theme.MensajeriaTheme
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray

/**
 * Pantalla de Inicio de Sesión Accesible:
 * Permite acceso manual con campos de texto accesibles o activación del modo por voz.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (UserProfile) -> Unit,
    onVoiceModeClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
    onManualLoginSubmit: (String, String, (UserProfile) -> Unit, (String) -> Unit) -> Unit
) {
    var userOrEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            VoiceAssistantLogo(
                size = 170.dp,
                isPulsing = false,
                onClick = onVoiceModeClick
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Bienvenido a Asistente\nde Voz",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Inicia sesión para continuar y acceder a tus mensajes.",
                fontSize = 15.sp,
                color = TextSecondaryGray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            AccessibleTextField(
                value = userOrEmail,
                onValueChange = {
                    userOrEmail = it
                    errorMessage = null
                },
                placeholder = "Usuario o correo electrónico",
                leadingIcon = Icons.Default.Person
            )

            Spacer(modifier = Modifier.height(16.dp))

            AccessibleTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                placeholder = "Contraseña",
                leadingIcon = Icons.Default.Lock,
                isPassword = true
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = ErrorRed,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AccessiblePrimaryButton(
                text = "Iniciar sesión",
                onClick = {
                    if (userOrEmail.isBlank()) {
                        errorMessage = "Por favor ingrese su usuario o correo."
                    } else {
                        onManualLoginSubmit(
                            userOrEmail,
                            password,
                            { user -> onLoginSuccess(user) },
                            { error -> errorMessage = error }
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "¿Olvidaste tu contraseña?",
                color = AccessibleYellow,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable {
                        onVoiceModeClick()
                    }
                    .semantics {
                        this.role = Role.Button
                        this.contentDescription = "¿Olvidaste tu contraseña? Toque para recuperar o ingresar por voz."
                    }
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF2C2C2E)
                )
                Text(
                    text = "Crear cuenta",
                    color = AccessibleYellow,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .clickable(onClick = onCreateAccountClick)
                        .semantics {
                            this.role = Role.Button
                            this.contentDescription = "Crear cuenta nueva en el sistema."
                        }
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF2C2C2E)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Alias para compatibilidad
@Composable
fun FigmaLoginScreen(
    onLoginSuccess: (UserProfile) -> Unit,
    onVoiceModeClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
    onManualLoginSubmit: (String, String, (UserProfile) -> Unit, (String) -> Unit) -> Unit
) = LoginScreen(onLoginSuccess, onVoiceModeClick, onCreateAccountClick, onManualLoginSubmit)

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LoginScreenPreview() {
    MensajeriaTheme {
        LoginScreen(
            onLoginSuccess = {},
            onVoiceModeClick = {},
            onCreateAccountClick = {},
            onManualLoginSubmit = { _, _, _, _ -> }
        )
    }
}
