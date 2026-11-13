package com.example.mensajeria.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import com.example.mensajeria.ui.components.exploreTarget
import com.example.mensajeria.ui.components.playAccessibleClickFeedback
import com.example.mensajeria.ui.components.VoiceAssistantLogo
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.MensajeriaTheme
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray

/**
 * Pantalla 2.1: Bienvenida accesible con división espacial táctil.
 * "Pulsar lado izquierdo para iniciar sesión si ya tiene una cuenta"
 * "Pulsar lado derecho para registrarse si aún no tiene una cuenta"
 */
@Composable
fun AccessibleWelcomeScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onChangeModeClick: () -> Unit = {},
    onSpeakAnnouncement: (String) -> Unit = {},
    onLeftTap: () -> Unit = {},
    onRightTap: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onSpeakAnnouncement(
            "Estás en la pantalla de bienvenida. Pulsa el lado izquierdo de la pantalla para iniciar sesión si ya tienes una cuenta. Pulsa el lado derecho para registrarse si aún no tienes una cuenta."
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Cabecera compacta con logotipo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Asistente de Voz",
                    color = AccessibleYellow,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )

                Text(
                    text = "Cambiar modo",
                    color = TextSecondaryGray,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .exploreTarget(id = "welcome_change_mode", label = "Cambiar modo")
                        .clickable(role = Role.Button) {
                            playAccessibleClickFeedback(context)
                            onChangeModeClick()
                        }
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            VoiceAssistantLogo(
                size = 110.dp,
                isPulsing = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Pantalla de Bienvenida",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dos grandes áreas táctiles: Izquierda (Login) y Derecha (Registro)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // LADO IZQUIERDO: Iniciar Sesión
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .exploreTarget(id = "welcome_login", label = "Iniciar sesión")
                        .clickable {
                            playAccessibleClickFeedback(context)
                            onLeftTap()
                            onLoginClick()
                        }
                        .semantics {
                            this.role = Role.Button
                            this.contentDescription = "Lado izquierdo: Iniciar sesión si ya tiene una cuenta."
                        },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBackground),
                    border = BorderStroke(3.dp, AccessibleYellow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(AccessibleYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                tint = PureBlack,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = "LADO IZQUIERDO",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccessibleYellow,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "INICIAR\nSESIÓN",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 30.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Pulsa aquí si ya tienes una cuenta.",
                            fontSize = 13.sp,
                            color = TextSecondaryGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // LADO DERECHO: Registrarse
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .exploreTarget(id = "welcome_register", label = "Registrarme")
                        .clickable {
                            playAccessibleClickFeedback(context)
                            onRightTap()
                            onRegisterClick()
                        }
                        .semantics {
                            this.role = Role.Button
                            this.contentDescription = "Lado derecho: Registrarse si aún no tiene una cuenta."
                        },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                    border = BorderStroke(2.dp, Color(0xFF666666))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2C2C2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AppRegistration,
                                contentDescription = null,
                                tint = AccessibleYellow,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = "LADO DERECHO",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccessibleYellow,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "REGISTRARME",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 30.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Pulsa aquí para crear una cuenta nueva.",
                            fontSize = 13.sp,
                            color = TextSecondaryGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AccessibleWelcomeScreenPreview() {
    MensajeriaTheme {
        AccessibleWelcomeScreen(
            onLoginClick = {},
            onRegisterClick = {}
        )
    }
}
