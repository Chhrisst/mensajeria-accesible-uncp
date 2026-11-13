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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.RegistrationForm
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.ui.theme.ErrorRed
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray
import com.example.mensajeria.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

/**
 * Pantalla de Registro Estándar (Modo Sin Discapacidad).
 */
@Composable
fun StandardRegisterScreen(
    authViewModel: AuthViewModel,
    onBackToLogin: () -> Unit,
    onRegistrationSuccess: (UserProfile) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var isCheckingUsername by remember { mutableStateOf(false) }
    var isUsernameAvailableState by remember { mutableStateOf<Boolean?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }
    val isLoading = authViewModel.uiState.collectAsState().value.isLoading

    val whatsappGreen = Color(0xFF25D366)
    val cardDark = Color(0xFF1F2C34)

    LaunchedEffect(username) {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.length < 3) {
            isUsernameAvailableState = null
            usernameError = null
            return@LaunchedEffect
        }
        delay(350)
        isCheckingUsername = true
        val available = authViewModel.checkUsernameAvailability(clean)
        isCheckingUsername = false
        isUsernameAvailableState = available
        if (!available) {
            usernameError = "El usuario @$clean ya está en uso. Elige otro."
        } else {
            usernameError = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C1317))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToLogin) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Crear Cuenta",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Ingresa tus datos para registrarte",
                fontSize = 15.sp,
                color = TextSecondaryGray
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it; nameError = null; submitError = null },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it, color = ErrorRed) } },
                enabled = !isLoading,
                label = { Text("Nombre completo") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = whatsappGreen) },
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
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { input ->
                    username = input.filter { it.isLetterOrDigit() || it == '_' || it == '@' }
                    usernameError = null
                    submitError = null
                },
                isError = usernameError != null,
                supportingText = {
                    if (usernameError != null) {
                        Text(usernameError!!, color = ErrorRed)
                    } else if (isCheckingUsername) {
                        Text("Verificando disponibilidad...", color = TextSecondaryGray)
                    } else if (isUsernameAvailableState == true) {
                        Text("✓ @${username.trim().removePrefix("@")} está disponible", color = Color(0xFF25D366))
                    } else {
                        Text("Identificador único para que otros te encuentren (ej: @carlos20)", color = TextSecondaryGray)
                    }
                },
                enabled = !isLoading,
                label = { Text("Nombre de usuario (@usuario)") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = whatsappGreen) },
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
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = null; submitError = null },
                isError = emailError != null,
                supportingText = emailError?.let { { Text(it, color = ErrorRed) } },
                enabled = !isLoading,
                label = { Text("Correo electrónico") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = whatsappGreen) },
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
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                enabled = !isLoading,
                label = { Text("Número de celular") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = whatsappGreen) },
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
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; passwordError = null; submitError = null },
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it, color = ErrorRed) } },
                enabled = !isLoading,
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = whatsappGreen) },
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
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val cleanUsername = username.trim().removePrefix("@").lowercase()
                    nameError = if (fullName.isBlank()) "Ingresa tu nombre completo." else null
                    usernameError = AuthViewModel.validateUsername(username)
                        ?: if (isUsernameAvailableState == false) "El usuario @$cleanUsername ya existe. Elige otro." else null
                    emailError = AuthViewModel.validateEmail(email)
                    passwordError = AuthViewModel.validatePassword(password)
                    if (nameError != null || usernameError != null || emailError != null || passwordError != null) return@Button
                    authViewModel.registerManual(
                        RegistrationForm(
                            fullName = fullName.trim(),
                            username = cleanUsername,
                            email = email.trim(),
                            phone = phone.trim(),
                            pin = password,
                            hasDisability = false,
                            visualCondition = "Sin discapacidad"
                        ),
                        onSuccess = { newUser -> onRegistrationSuccess(newUser) },
                        onError = { err -> submitError = err }
                    )
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
                        text = "Registrarme",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            submitError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = ErrorRed, fontSize = 14.sp)
            }
        }
    }
}
