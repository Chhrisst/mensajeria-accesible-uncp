package com.example.mensajeria.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.ui.components.AccessiblePrimaryButton
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.MensajeriaTheme
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray

/**
 * Pantalla 0: Solicitud inicial de Permisos Requeridos (Micrófono).
 */
@Composable
fun PermissionScreen(
    onRequestPermission: () -> Unit,
    onSpeakAnnouncement: (String) -> Unit = {}
) {
    LaunchedEffect(Unit) {
        onSpeakAnnouncement(
            "Permiso necesario. Para que la aplicación pueda escucharte y transcribir tus mensajes por voz, se necesita el permiso de micrófono. Toca el botón en pantalla para conceder el permiso."
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(AccessibleYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = PureBlack,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = "Permiso de Micrófono\nRequerido",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccessibleYellow,
                    textAlign = TextAlign.Center,
                    lineHeight = 34.sp,
                    modifier = Modifier.semantics { heading() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Nuestra aplicación utiliza el micrófono para permitirte navegar, iniciar sesión, dictar notas y enviar mensajes totalmente por voz con asistencia auditiva.",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Tu privacidad está protegida. El audio solo se procesa de forma segura en tu dispositivo para transcribir tus palabras.",
                    fontSize = 14.sp,
                    color = TextSecondaryGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            AccessiblePrimaryButton(
                text = "Conceder Permiso de Micrófono",
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PermissionScreenPreview() {
    MensajeriaTheme {
        PermissionScreen(onRequestPermission = {})
    }
}
