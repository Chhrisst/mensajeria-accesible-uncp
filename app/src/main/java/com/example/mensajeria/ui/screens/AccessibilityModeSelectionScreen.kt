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
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.PhoneAndroid
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
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.MensajeriaTheme
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray

/**
 * Pantalla 1: Selección inicial de accesibilidad.
 * Pregunta si el usuario tiene discapacidad visual o no para configurar la experiencia.
 */
@Composable
fun AccessibilityModeSelectionScreen(
    onSelectMode: (hasDisability: Boolean) -> Unit,
    onSpeakAnnouncement: (String) -> Unit = {}
) {
    LaunchedEffect(Unit) {
        onSpeakAnnouncement(
            "Bienvenido. Por favor elige tu modo de uso. Pulsa el lado izquierdo si tienes discapacidad visual para activar el narrador de voz, retroalimentación táctil y controles laterales. Pulsa el lado derecho si no tienes discapacidad para usar la interfaz estándar tipo mensajería instantánea."
        )
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
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "¿Cómo deseas usar la app?",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Selecciona la opción adaptada a tus necesidades.",
                fontSize = 15.sp,
                color = TextSecondaryGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Pantalla dividida en dos paneles interactivos grandes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Opción 1: LADO IZQUIERDO -> Con discapacidad visual (Modo Asistido)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelectMode(true) }
                        .semantics {
                            this.role = Role.Button
                            this.contentDescription = "Lado izquierdo: Tengo discapacidad visual. Activa narrador de voz, vibración y navegación táctil."
                        },
                    shape = RoundedCornerShape(20.dp),
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
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(AccessibleYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Accessible,
                                contentDescription = null,
                                tint = PureBlack,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "LADO IZQUIERDO",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccessibleYellow,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Tengo\ndiscapacidad\nvisual",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 26.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "• Narrador de voz activo\n• Navegación por arrastre\n• Selección por lados\n• Dictado de mensajes",
                            fontSize = 13.sp,
                            color = TextSecondaryGray,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Opción 2: LADO DERECHO -> Sin discapacidad (Modo Estándar tipo WhatsApp)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelectMode(false) }
                        .semantics {
                            this.role = Role.Button
                            this.contentDescription = "Lado derecho: Sin discapacidad visual. Modo estándar visual tipo WhatsApp."
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141A1F)),
                    border = BorderStroke(1.5.dp, Color(0xFF25D366))
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
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF25D366)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = PureBlack,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "LADO DERECHO",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF25D366),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Sin\ndiscapacidad\nvisual",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 26.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "• Modo estándar WhatsApp\n• Teclado táctil en pantalla\n• Sin narrador obligatorio\n• Mensajería instantánea",
                            fontSize = 13.sp,
                            color = TextSecondaryGray,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AccessibilityModeSelectionScreenPreview() {
    MensajeriaTheme {
        AccessibilityModeSelectionScreen(onSelectMode = {})
    }
}
