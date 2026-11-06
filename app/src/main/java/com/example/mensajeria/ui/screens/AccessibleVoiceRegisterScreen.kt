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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.RegistrationForm
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

/**
 * Pantalla 2.3: Registro accesible guiado totalmente por voz y clave de toques laterales.
 */
@Composable
fun AccessibleVoiceRegisterScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onRegistrationSuccess: (UserProfile) -> Unit
) {
    var registeredName by remember { mutableStateOf("") }
    var sidePatternEntered by remember { mutableStateOf("") }
    var feedbackText by remember { mutableStateOf("Diga su nombre completo en voz alta...") }
    var isListening by remember { mutableStateOf(false) }

    fun startListeningForName() {
        registeredName = ""
        sidePatternEntered = ""
        feedbackText = "Diga su nombre completo en voz alta..."
        isListening = true

        authViewModel.ttsService.speak(
            "Por favor, di tu nombre completo después del tono.",
            onDone = {
                authViewModel.startRegistrationVoiceInput(
                    onResult = { recognizedName ->
                        isListening = false
                        registeredName = recognizedName
                        feedbackText = "Nombre recibido: $recognizedName"
                        authViewModel.ttsService.speak(
                            "Nombre recibido: $recognizedName. Ahora crea tu contraseña de tres toques: toca el lado izquierdo o derecho tres veces."
                        )
                    },
                    onError = { error ->
                        isListening = false
                        feedbackText = "No se escuchó claramente. Presiona Reintentar Dictado."
                        authViewModel.ttsService.speak(
                            "No se escuchó claramente. Toca el botón Reintentar Dictado para intentarlo de nuevo."
                        )
                    }
                )
            }
        )
    }

    LaunchedEffect(Unit) {
        startListeningForName()
    }

    fun handleSideTap(side: Char) {
        if (registeredName.isBlank()) return

        val sideName = if (side == 'L') "Izquierda" else "Derecha"
        authViewModel.ttsService.speak(sideName)

        val newPattern = sidePatternEntered + side
        sidePatternEntered = newPattern

        if (newPattern.length == 3) {
            val newUser = authViewModel.authRepository.registerUser(
                RegistrationForm(
                    fullName = registeredName,
                    pin = newPattern,
                    hasDisability = true,
                    visualCondition = "Ceguera total"
                )
            )
            authViewModel.ttsService.speak(
                "Registro completado exitosamente. Bienvenido ${newUser.name} a Mensajería UNCP."
            )
            onRegistrationSuccess(newUser)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AccessibleBackButton(onClick = onBack)
                Text(
                    text = "Registro por Voz",
                    color = AccessibleYellow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (registeredName.isBlank()) {
                // FASE 1: DICTADO DEL NOMBRE
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    VoiceAssistantLogo(
                        size = 180.dp,
                        isPulsing = isListening,
                        onClick = { startListeningForName() }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Di tu nombre completo",
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
                        text = if (isListening) "Escuchando tu voz..." else "Reintentar Dictado",
                        onClick = { startListeningForName() },
                        leadingIcon = Icons.Default.Mic
                    )
                }
            } else {
                // FASE 2: CREACIÓN DE CLAVE POR TOQUES LATERALES (3 TOQUES)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hola, $registeredName",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccessibleYellow
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Crea tu clave tocando 3 veces los lados",
                        fontSize = 15.sp,
                        color = TextSecondaryGray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PinDotIndicator(
                        enteredLength = sidePatternEntered.length,
                        maxDigits = 3
                    )

                    Spacer(modifier = Modifier.height(20.dp))

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

                    AccessibleOutlinedButton(
                        text = "Reiniciar clave",
                        onClick = {
                            sidePatternEntered = ""
                            authViewModel.ttsService.speak("Clave reiniciada. Ingresa tres toques nuevamente.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
