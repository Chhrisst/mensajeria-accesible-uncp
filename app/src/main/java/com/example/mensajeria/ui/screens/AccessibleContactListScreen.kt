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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import com.example.mensajeria.domain.service.VoiceAssistantManager
import com.example.mensajeria.domain.service.VoiceAssistantState
import com.example.mensajeria.ui.components.AccessibleVoiceAssistantBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.Contact
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray
import com.example.mensajeria.ui.viewmodel.AuthViewModel
import kotlin.math.abs

/**
 * Pantalla 2.4: Lista de contactos accesible.
 * - Arriba solo el nombre del usuario y botón de salida.
 * - Indicaciones ultra breves dichas solo una vez ("Contactos. Desliza para explorar.").
 * - Al explorar con el dedo solo dice el nombre del contacto.
 * - Para entrar al chat se pulsan ambos lados simultáneamente o con dos dedos.
 */
@Composable
fun AccessibleContactListScreen(
    currentUser: UserProfile,
    contacts: List<Contact>,
    authViewModel: AuthViewModel,
    voiceAssistantManager: VoiceAssistantManager? = null,
    onSelectContact: (Contact) -> Unit,
    onLogout: () -> Unit
) {
    var focusedIndex by remember { mutableIntStateOf(0) }
    val assistantState = voiceAssistantManager?.state?.collectAsState()?.value ?: VoiceAssistantState()

    // Indicación ultra breve y concisa, dicha solo una vez
    LaunchedEffect(Unit) {
        authViewModel.ttsService.speak("Contactos. Desliza para explorar.")
    }

    fun focusContact(index: Int) {
        val clamped = index.coerceIn(0, (contacts.size - 1).coerceAtLeast(0))
        if (clamped != focusedIndex && clamped < contacts.size) {
            focusedIndex = clamped
            authViewModel.triggerHoverVibration()
            val contact = contacts[clamped]
            // Solo dice el nombre del contacto, nada más
            authViewModel.ttsService.speak(contact.name)
        }
    }

    fun confirmSelection() {
        if (contacts.isNotEmpty()) {
            val selected = contacts[focusedIndex]
            authViewModel.triggerBothSidesSelection()
            authViewModel.soundService.playSuccessSound()
            authViewModel.ttsService.speak("Abriendo chat con ${selected.name}.")
            onSelectContact(selected)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .navigationBarsPadding()
            .pointerInput(contacts) {
                awaitPointerEventScope {
                    var lastLeftTouch = 0L
                    var lastRightTouch = 0L
                    var lastConfirmTime = 0L

                    while (true) {
                        val event = awaitPointerEvent()
                        val now = System.currentTimeMillis()
                        val width = size.width
                        val height = size.height

                        val pressedPointers = event.changes.filter { it.pressed }
                        // 1. Detección de pulsar ambos lados (2 dedos simultáneos)
                        if (pressedPointers.size >= 2) {
                            val hasLeft = pressedPointers.any { it.position.x < width * 0.45f }
                            val hasRight = pressedPointers.any { it.position.x > width * 0.55f }
                            if ((hasLeft && hasRight || pressedPointers.size >= 2) && now - lastConfirmTime > 1000L) {
                                lastConfirmTime = now
                                event.changes.forEach { it.consume() }
                                confirmSelection()
                            }
                        } else if (pressedPointers.size == 1) {
                            val change = pressedPointers[0]
                            val x = change.position.x
                            val y = change.position.y

                            if (x < width * 0.40f) {
                                lastLeftTouch = now
                            } else if (x > width * 0.60f) {
                                lastRightTouch = now
                            }

                            // Toque de ambos lados casi simultáneo (<500ms)
                            if (abs(lastLeftTouch - lastRightTouch) < 500L && now - lastConfirmTime > 1000L) {
                                lastConfirmTime = now
                                lastLeftTouch = 0L
                                lastRightTouch = 0L
                                change.consume()
                                confirmSelection()
                            } else {
                                // Exploración táctil por deslizamiento vertical
                                val headerOffset = 80.dp.toPx()
                                val availableHeight = (height - headerOffset).coerceAtLeast(1f)
                                val itemHeight = availableHeight / contacts.size.coerceAtLeast(1)
                                val index = ((y - headerOffset) / itemHeight).toInt().coerceIn(0, contacts.size - 1)
                                focusContact(index)
                            }
                        }
                    }
                }
            }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Barra superior: Solo el nombre del usuario y botón de cerrar sesión
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentUser.name,
                    color = AccessibleYellow,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )

                // Botón para cerrar sesión
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(DarkCardBackground)
                        .border(1.5.dp, AccessibleYellow.copy(alpha = 0.6f), CircleShape)
                        .clickable {
                            authViewModel.soundService.playButtonClickSound()
                            authViewModel.vibrationService.vibrateClick()
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

            Spacer(modifier = Modifier.height(16.dp))

            // Tarjetas de contactos ocupando armoniosamente el espacio
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                contacts.forEachIndexed { index, contact ->
                    val isFocused = index == focusedIndex
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clickable {
                                focusContact(index)
                                confirmSelection()
                            }
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = contact.name
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFocused) AccessibleYellow else DarkCardBackground
                        ),
                        border = BorderStroke(2.5.dp, AccessibleYellow)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(if (isFocused) PureBlack else Color(0xFF2C2C2E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isFocused) AccessibleYellow else Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Spacer(modifier = Modifier.size(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocused) PureBlack else Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
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

            Spacer(modifier = Modifier.height(14.dp))

            // BARRA INTERACTIVA DEL ASISTENTE DE VOZ INTELIGENTE CON GEMINI
            if (voiceAssistantManager != null) {
                AccessibleVoiceAssistantBar(
                    state = assistantState,
                    onAssistantClick = {
                        voiceAssistantManager.startListeningCycle(explicitPrompt = true)
                    }
                )
            }
        }
    }
}
