package com.example.mensajeria.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.domain.service.VoiceAssistantState
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.DarkCardBackground
import com.example.mensajeria.ui.theme.PureBlack

/**
 * Barra o tarjeta interactiva del Asistente de Voz Inteligente UNCP.
 * Proporciona una zona táctil amplia de fácil localización y estados accesibles multimodales.
 */
@Composable
fun AccessibleVoiceAssistantBar(
    state: VoiceAssistantState,
    onAssistantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "assistant_pulse")
    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state.isListening || state.isProcessing) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val containerBg = when {
        state.isProcessing -> AccessibleYellow
        state.isListening -> AccessibleYellow.copy(alpha = 0.25f)
        state.isSpeaking -> DarkCardBackground
        else -> DarkCardBackground
    }

    val iconColor = when {
        state.isProcessing -> PureBlack
        state.isListening -> AccessibleYellow
        state.isSpeaking -> AccessibleYellow
        else -> AccessibleYellow
    }

    val statusTitle = when {
        state.isProcessing -> "Asistente pensando..."
        state.isListening -> "Escuchando comando..."
        state.isSpeaking -> "Asistente respondiendo..."
        else -> "Asistente de Voz IA"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onAssistantClick)
            .semantics {
                this.role = Role.Button
                this.contentDescription = "$statusTitle. ${state.feedbackMessage}. Toca para hablarle al asistente."
                this.liveRegion = LiveRegionMode.Polite
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = BorderStroke(2.5.dp, AccessibleYellow)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Ícono dinámico del Asistente con pulso
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(if (state.isListening || state.isProcessing) scaleAnim else 1f)
                    .background(
                        color = if (state.isProcessing) PureBlack else AccessibleYellow.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        state.isProcessing -> Icons.Default.AutoAwesome
                        state.isSpeaking -> Icons.Default.VolumeUp
                        else -> Icons.Default.Mic
                    },
                    contentDescription = null,
                    tint = if (state.isProcessing) AccessibleYellow else iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.size(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = statusTitle,
                    color = if (state.isProcessing) PureBlack else AccessibleYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.feedbackMessage,
                    color = if (state.isProcessing) PureBlack.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f),
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
