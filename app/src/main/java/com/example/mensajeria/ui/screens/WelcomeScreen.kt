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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.ui.components.AccessibleOutlinedButton
import com.example.mensajeria.ui.components.VoiceAssistantLogo
import com.example.mensajeria.ui.theme.AccessibleYellow
import com.example.mensajeria.ui.theme.MensajeriaTheme
import com.example.mensajeria.ui.theme.PureBlack

/**
 * Pantalla de Bienvenida y Onboarding accesible:
 * Emblema circular central del Asistente de Voz y botón directo "Comenzar".
 */
@Composable
fun WelcomeScreen(
    onStartClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
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
                modifier = Modifier.padding(top = 40.dp)
            ) {
                VoiceAssistantLogo(
                    size = 190.dp,
                    isPulsing = true,
                    onClick = onStartClick
                )

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "Bienvenido a Asistente\nde Voz",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccessibleYellow,
                    textAlign = TextAlign.Center,
                    lineHeight = 34.sp,
                    modifier = Modifier.semantics { heading() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tu voz, nuestra prioridad. Comunícate de forma fácil, segura y sin barreras.",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            AccessibleOutlinedButton(
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
