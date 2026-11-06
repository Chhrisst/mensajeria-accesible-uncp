package com.example.mensajeria.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.ui.components.AccessiblePrimaryButton
import com.example.mensajeria.ui.components.VoiceAssistantLogo
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.MensajeriaTheme
import com.example.mensajeria.ui.theme.PureBlack

/**
 * Pantalla inicial de Bienvenida:
 * Narrador automático de bienvenida con TTS: "Bienvenido usuario."
 * Emblema circular del Asistente de Voz y botón directo "Comenzar".
 */
@Composable
fun WelcomeScreen(
    onStartClick: () -> Unit,
    onSpeakAnnouncement: (String) -> Unit = {}
) {
    LaunchedEffect(Unit) {
        onSpeakAnnouncement("Bienvenido usuario. Toca la pantalla o presiona el botón comenzar para continuar.")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .clickable { onStartClick() }
            .semantics {
                this.role = Role.Button
                this.contentDescription = "Pantalla de bienvenida. Toque en cualquier lugar para comenzar."
            }
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 30.dp)
            ) {
                VoiceAssistantLogo(
                    size = 180.dp,
                    isPulsing = true,
                    onClick = onStartClick
                )

                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = "Bienvenido usuario",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccessibleYellow,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                    modifier = Modifier.semantics { heading() }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Mensajería accesible asistida por inteligencia artificial.",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Tu voz, nuestra prioridad. Comunícate de forma fácil, autónoma y sin barreras.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            AccessiblePrimaryButton(
                text = "Comenzar",
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
    }
}

// Alias para compatibilidad
@Composable
fun FigmaWelcomeScreen(onStartClick: () -> Unit) = WelcomeScreen(onStartClick)

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun WelcomeScreenPreview() {
    MensajeriaTheme {
        WelcomeScreen(onStartClick = {})
    }
}
