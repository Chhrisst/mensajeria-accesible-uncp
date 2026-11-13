package com.example.mensajeria.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.Message
import com.example.mensajeria.ui.theme.HighContrastYellow
import com.example.mensajeria.ui.theme.MessageReceivedBubble
import com.example.mensajeria.ui.theme.MessageSentBubble
import com.example.mensajeria.ui.theme.SurfaceDark
import com.example.mensajeria.ui.theme.TextDark
import com.example.mensajeria.ui.theme.TextPrimary
import com.example.mensajeria.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Botón accesible de gran tamaño (mínimo 60dp de altura) con semántica explícita para TalkBack.
 */
@Composable
fun AccessibleButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = HighContrastYellow,
    textColor: Color = TextDark,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor,
            disabledContainerColor = Color.Gray,
            disabledContentColor = Color.DarkGray
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .semantics {
                this.role = Role.Button
                this.contentDescription = contentDescription
            }
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Tarjeta de Sugerencia Inteligente (Smart Reply) asistida por IA.
 * Permite responder de inmediato al pulsarla.
 */
@Composable
fun SmartReplyCard(
    replyText: String,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(2.dp, HighContrastYellow),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .semantics {
                this.role = Role.Button
                this.contentDescription = "Sugerencia rápida de Inteligencia Artificial número ${index + 1}: $replyText. Pulse dos veces para enviar."
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "⚡ Sugerencia ${index + 1}: $replyText",
                color = HighContrastYellow,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Burbuja de mensaje con semántica accesible y soporte para re-escuchar al hacer click.
 */
@Composable
fun MessageBubble(
    message: Message,
    onSpeakMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPlayingVoiceNote: Boolean = false
) {
    val isSent = message.isOutgoing
    val bubbleColor = if (isSent) MessageSentBubble else MessageReceivedBubble
    val timeFormatted = if (message.formattedTime.isNotBlank()) {
        message.formattedTime
    } else {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bubbleColor),
        border = BorderStroke(1.dp, if (isSent) HighContrastYellow else Color.Gray),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSpeakMessage(message.text) }
            .semantics {
                // Solo decir el mensaje tal cual al explorar (sin anteponer nombre ni prefijos)
                this.contentDescription = message.text
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = timeFormatted,
                color = if (isSent) HighContrastYellow else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (message.isVoiceNote && isPlayingVoiceNote) {
                    "▶ Reproduciendo: ${message.text}"
                } else {
                    message.text
                },
                color = TextPrimary,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
