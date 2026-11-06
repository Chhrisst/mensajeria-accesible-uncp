package com.example.mensajeria.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.ui.components.AccessiblePrimaryButton
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray
import com.example.mensajeria.ui.viewmodel.AuthViewModel

/**
 * Pantalla 2.4: Lista de contactos con navegación háptica por arrastre de dedo
 * y selección mediante pulsación en ambos lados de la pantalla.
 */
@Composable
fun AccessibleContactListScreen(
    currentUser: UserProfile,
    contacts: List<Contact>,
    authViewModel: AuthViewModel,
    onSelectContact: (Contact) -> Unit,
    onLogout: () -> Unit
) {
    var focusedIndex by remember { mutableIntStateOf(0) }
    var hasLeftPressed by remember { mutableStateOf(false) }
    var hasRightPressed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authViewModel.ttsService.speak(
            "Estás en la lista de contactos. Arrastra tu dedo por la pantalla para explorar; el celular vibrará y te dirá cada contacto. Para entrar al chat, pulsa simultáneamente ambos lados de la pantalla."
        )
    }

    fun focusContact(index: Int) {
        val clamped = index.coerceIn(0, (contacts.size - 1).coerceAtLeast(0))
        if (clamped != focusedIndex && clamped < contacts.size) {
            focusedIndex = clamped
            authViewModel.triggerHoverVibration()
            val contact = contacts[clamped]
            authViewModel.ttsService.speak("${contact.name}. ${contact.description}")
        }
    }

    fun confirmSelection() {
        if (contacts.isNotEmpty()) {
            val selected = contacts[focusedIndex]
            authViewModel.triggerBothSidesSelection()
            authViewModel.ttsService.speak("Abriendo chat con ${selected.name}.")
            onSelectContact(selected)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .pointerInput(contacts) {
                // Rastrear arrastre de dedo por la pantalla (Touch Exploration)
                detectDragGestures { change, _ ->
                    val y = change.position.y
                    // Mapear posición Y estimada a cada contacto
                    val index = ((y - 300f) / 250f).toInt().coerceIn(0, contacts.size - 1)
                    focusContact(index)
                }
            }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Barra superior
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Lista de Contactos",
                            color = AccessibleYellow,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Socio activo: ${currentUser.name}",
                            color = TextSecondaryGray,
                            fontSize = 14.sp
                        )
                    }

                    // Botón para cerrar sesión
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(DarkCardBackground)
                            .clickable {
                                authViewModel.ttsService.speak("Cerrando sesión.")
                                onLogout()
                            }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "Cerrar sesión"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            tint = AccessibleYellow,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Arrastra el dedo para explorar. Contacto enfocado:",
                    color = Color.White,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tarjetas de contactos accesibles
                contacts.forEachIndexed { index, contact ->
                    val isFocused = index == focusedIndex
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                focusContact(index)
                                confirmSelection()
                            }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "${contact.name}. ${contact.description}. ${if (isFocused) "Enfocado" else ""}"
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFocused) AccessibleYellow else DarkCardBackground
                        ),
                        border = BorderStroke(2.dp, AccessibleYellow)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(if (isFocused) PureBlack else Color(0xFF2C2C2E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isFocused) AccessibleYellow else Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.size(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocused) PureBlack else Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = contact.description,
                                    fontSize = 13.sp,
                                    color = if (isFocused) Color(0xFF262626) else TextSecondaryGray
                                )
                            }

                            if (isFocused) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = PureBlack,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            // CONTROLES DE CONFIRMACIÓN LATERAL (PULSAR AMBOS LADOS PARA ENTRAR)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Para entrar al chat pulsa ambos lados de la pantalla:",
                    color = TextSecondaryGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Lado Izquierdo
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clickable {
                                hasLeftPressed = true
                                authViewModel.triggerHoverVibration()
                                authViewModel.ttsService.speak("Lado izquierdo presionado.")
                                if (hasRightPressed) {
                                    confirmSelection()
                                    hasLeftPressed = false
                                    hasRightPressed = false
                                }
                            }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "Pulsar lado izquierdo"
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasLeftPressed) AccessibleYellow else DarkCardBackground
                        ),
                        border = BorderStroke(2.dp, AccessibleYellow)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "LADO IZQUIERDO",
                                color = if (hasLeftPressed) PureBlack else AccessibleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Lado Derecho
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clickable {
                                hasRightPressed = true
                                authViewModel.triggerHoverVibration()
                                authViewModel.ttsService.speak("Lado derecho presionado.")
                                if (hasLeftPressed) {
                                    confirmSelection()
                                    hasLeftPressed = false
                                    hasRightPressed = false
                                }
                            }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "Pulsar lado derecho"
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasRightPressed) AccessibleYellow else DarkCardBackground
                        ),
                        border = BorderStroke(2.dp, AccessibleYellow)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "LADO DERECHO",
                                color = if (hasRightPressed) PureBlack else AccessibleYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Botón central de respaldo
                AccessiblePrimaryButton(
                    text = "Entrar a conversación",
                    onClick = { confirmSelection() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
