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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.MensajeriaTheme
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray

/**
 * Pantalla 1: Selección inicial de modo de accesibilidad.
 * Perfectamente ajustada y dimensionada para cualquier tamaño de pantalla y barra de navegación Android.
 */
@Composable
fun AccessibilityModeSelectionScreen(
    onSelectMode: (hasDisability: Boolean) -> Unit,
    onSpeakAnnouncement: (String) -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onSpeakAnnouncement(
            "¿Cómo deseas usar la app? Pulsa el lado izquierdo para Modo Accesible si tienes discapacidad visual. Pulsa el lado derecho para Modo Estándar sin discapacidad."
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "¿Cómo deseas usar la app?",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AccessibleYellow,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Selecciona la opción adaptada a tus necesidades.",
                fontSize = 13.5.sp,
                color = TextSecondaryGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Pantalla dividida en dos paneles interactivos grandes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Opción 1: LADO IZQUIERDO -> Modo Accesible (Discapacidad visual)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .exploreTarget(id = "mode_accessible", label = "Modo accesible")
                        .clickable {
                            playAccessibleClickFeedback(context)
                            onSelectMode(true)
                        }
                        .semantics {
                            this.role = Role.Button
                            this.contentDescription = "Lado izquierdo: Modo Accesible. Diseñado para personas con discapacidad visual. Activa narrador de voz, dictado y navegación por lados."
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBackground),
                    border = BorderStroke(2.dp, AccessibleYellow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Insignia Lado Izquierdo
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccessibleYellow.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "LADO IZQUIERDO",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccessibleYellow,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Ícono de Asistencia de Voz
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(AccessibleYellow),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = PureBlack,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Modo\nAccesible",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 21.sp
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = "Discapacidad visual",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccessibleYellow,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Lista de beneficios (concisa, 1 línea por viñeta)
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "• Narrador de voz\n• Navegación táctil\n• Selección lateral\n• Dictado de mensajes",
                                fontSize = 11.sp,
                                color = TextSecondaryGray,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }

                // Opción 2: LADO DERECHO -> Modo Estándar (Sin discapacidad visual)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .exploreTarget(id = "mode_standard", label = "Modo estándar")
                        .clickable {
                            playAccessibleClickFeedback(context)
                            onSelectMode(false)
                        }
                        .semantics {
                            this.role = Role.Button
                            this.contentDescription = "Lado derecho: Modo Estándar. Para personas sin discapacidad visual. Interfaz convencional con teclado en pantalla."
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141A1F)),
                    border = BorderStroke(2.dp, Color(0xFF25D366))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Insignia Lado Derecho
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF25D366).copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "LADO DERECHO",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF25D366),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Ícono de Celular estándar
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF25D366)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = PureBlack,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Modo\nEstándar",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 21.sp
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = "Sin discapacidad",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF25D366),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Lista de beneficios (concisa, 1 línea por viñeta)
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "• Modo tipo WhatsApp\n• Teclado en pantalla\n• Lectura visual\n• Mensajería directa",
                                fontSize = 11.sp,
                                color = TextSecondaryGray,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
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
