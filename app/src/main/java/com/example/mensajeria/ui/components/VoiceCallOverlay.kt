package com.example.mensajeria.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.PureBlack
import java.util.Locale

/**
 * Pantalla superpuesta de llamada de voz compartida por los modos Estándar y Accesible.
 */
@Composable
fun VoiceCallOverlay(
    contactName: String,
    statusText: String,
    durationSeconds: Int,
    isMuted: Boolean,
    isSpeakerphoneOn: Boolean,
    isAccessibleMode: Boolean,
    isIncomingRinging: Boolean = false,
    onAcceptCall: () -> Unit = {},
    onRejectCall: () -> Unit = {},
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    val accent = if (isAccessibleMode) AccessibleYellow else Color(0xFF25D366)
    val bg = if (isAccessibleMode) PureBlack else Color(0xFF0B141A)
    val infinite = rememberInfiniteTransition(label = "call_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "call_avatar"
    )

    val formattedTimer = when {
        durationSeconds > 0 -> String.format(Locale.getDefault(), "%02d:%02d", durationSeconds / 60, durationSeconds % 60)
        isIncomingRinging -> "Tocando..."
        else -> statusText
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 36.dp)
            ) {
                Text(
                    text = if (isIncomingRinging) "Llamada de voz entrante" else "Llamada de voz cifrada",
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(DarkCardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = contactName,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formattedTimer,
                    color = if (durationSeconds > 0) accent else Color(0xFFB0BEC5),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (isIncomingRinging) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Contestar
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .exploreTarget(
                                    id = "call_accept",
                                    label = "Contestar llamada de $contactName"
                                )
                                .clip(CircleShape)
                                .background(accent)
                                .clickable(role = Role.Button, onClick = onAcceptCall)
                                .semantics {
                                    this.role = Role.Button
                                    this.contentDescription = "Contestar llamada de $contactName"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = PureBlack,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Contestar", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    // Rechazar
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .exploreTarget(
                                    id = "call_reject",
                                    label = "Rechazar llamada de $contactName"
                                )
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                                .clickable(role = Role.Button, onClick = onRejectCall)
                                .semantics {
                                    this.role = Role.Button
                                    this.contentDescription = "Rechazar llamada de $contactName"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Rechazar", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // Altavoz
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .exploreTarget(id = "call_speaker", label = if (isSpeakerphoneOn) "Desactivar altavoz" else "Activar altavoz")
                            .clip(CircleShape)
                            .background(if (isSpeakerphoneOn) accent else DarkCardBackground)
                            .clickable(role = Role.Button, onClick = onToggleSpeaker)
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = if (isSpeakerphoneOn) "Altavoz activado" else "Altavoz desactivado"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (isSpeakerphoneOn) PureBlack else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Altavoz", color = Color.White, fontSize = 13.sp)
                }

                // Colgar
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .exploreTarget(id = "call_hangup", label = "Colgar llamada")
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                            .clickable(role = Role.Button, onClick = onEndCall)
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = "Finalizar llamada"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Colgar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // Silenciar
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .exploreTarget(id = "call_mute", label = if (isMuted) "Activar micrófono" else "Silenciar micrófono")
                            .clip(CircleShape)
                            .background(if (isMuted) accent else DarkCardBackground)
                            .clickable(role = Role.Button, onClick = onToggleMute)
                            .semantics {
                                this.role = Role.Button
                                this.contentDescription = if (isMuted) "Micrófono silenciado" else "Silenciar micrófono"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isMuted) PureBlack else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(if (isMuted) "Silenciado" else "Silenciar", color = Color.White, fontSize = 13.sp)
                }
                }
            }
        }
    }
}
