package com.example.mensajeria.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.ui.theme.ErrorRed
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray
import com.example.mensajeria.ui.viewmodel.AuthViewModel

/**
 * Pantalla 3.1: Inicio de sesión estándar (Modo Sin Discapacidad).
 * Interfaz visual moderna, fluida y sin narrador de voz forzado.
 */
@Composable
fun StandardLoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: (UserProfile) -> Unit,
    onGoToRegister: () -> Unit,
    onChangeMode: () -> Unit
) {
    var userOrEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var userError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isLoading = authViewModel.uiState.collectAsState().value.isLoading

    val whatsappGreen = Color(0xFF25D366)
    val cardDark = Color(0xFF1F2C34)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C1317))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Cambiar a modo asistido",
                        color = Color(0xFF25D366),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(onClick = onChangeMode)
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(whatsappGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = PureBlack,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Messenger",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Inicia sesión para chatear con tus contactos",
                    fontSize = 14.sp,
                    color = TextSecondaryGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Campo Usuario
                OutlinedTextField(
                    value = userOrEmail,
                    onValueChange = {
                        userOrEmail = it
                        userError = null
                        errorMessage = null
                    },
                    isError = userError != null,
                    supportingText = userError?.let { { Text(it, color = ErrorRed) } },
                    enabled = !isLoading,
                    label = { Text("Usuario o correo") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = whatsappGreen)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = cardDark,
                        unfocusedContainerColor = cardDark,
                        focusedBorderColor = whatsappGreen,
                        unfocusedBorderColor = Color(0xFF2A3942),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = whatsappGreen,
                        unfocusedLabelColor = TextSecondaryGray
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Campo Contraseña
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = null
                        errorMessage = null
                    },
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it, color = ErrorRed) } },
                    enabled = !isLoading,
                    label = { Text("Contraseña o PIN") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = whatsappGreen)
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = cardDark,
                        unfocusedContainerColor = cardDark,
                        focusedBorderColor = whatsappGreen,
                        unfocusedBorderColor = Color(0xFF2A3942),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = whatsappGreen,
                        unfocusedLabelColor = TextSecondaryGray
                    ),
                    singleLine = true
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = ErrorRed,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        userError = AuthViewModel.validateUserOrEmail(userOrEmail)
                        passwordError = if (password.isBlank()) "Ingresa tu contraseña o PIN (no solo espacios)." else null
                        if (userError == null && passwordError == null) {
                            authViewModel.loginManual(
                                userOrEmail,
                                password,
                                onSuccess = { user -> onLoginSuccess(user) },
                                onError = { err -> errorMessage = err }
                            )
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = whatsappGreen,
                        contentColor = PureBlack
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = PureBlack, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "Iniciar Sesión",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A3942))
                    Text(
                        text = "¿No tienes cuenta? Regístrate aquí",
                        color = whatsappGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clickable(onClick = onGoToRegister)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A3942))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
