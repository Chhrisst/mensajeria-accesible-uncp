package com.example.mensajeria.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mensajeria.data.model.CountryPhoneCatalog
import com.example.mensajeria.data.model.RegistrationForm
import com.example.mensajeria.data.model.UserProfile
import com.example.mensajeria.ui.theme.ErrorRed
import com.example.mensajeria.ui.theme.PureBlack
import com.example.mensajeria.ui.theme.TextSecondaryGray
import com.example.mensajeria.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

/**
 * Pantalla de Registro Estándar (Modo Sin Discapacidad).
 * - Selector de país mundial con autodetección por IP y límite exacto de dígitos numéricos.
 * - Barra deslizante de fuerza de contraseña y requisitos de seguridad (mayúscula, número y símbolo).
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
    var selectedCountry by remember { mutableStateOf(CountryPhoneCatalog.peru) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var countrySearchQuery by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var isCheckingUsername by remember { mutableStateOf(false) }
    var isUsernameAvailableState by remember { mutableStateOf<Boolean?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }
    val isLoading = authViewModel.uiState.collectAsState().value.isLoading

    val whatsappGreen = Color(0xFF25D366)
    val cardDark = Color(0xFF1F2C34)
    val darkBg = Color(0xFF0C1317)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = cardDark,
        unfocusedContainerColor = cardDark,
        focusedBorderColor = whatsappGreen,
        unfocusedBorderColor = Color(0xFF2A3942),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = whatsappGreen,
        unfocusedLabelColor = TextSecondaryGray
    )

    // Autodetectar país aproximado por IP pública al abrir la pantalla
    LaunchedEffect(Unit) {
        val detectedIso = CountryPhoneCatalog.detectCountryIsoByIp()
        val matched = CountryPhoneCatalog.findByIso(detectedIso)
        selectedCountry = matched
        if (phone.length > matched.maxDigits) {
            phone = phone.take(matched.maxDigits)
        }
    }

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
        usernameError = if (!available) "El usuario @$clean ya está en uso. Elige otro." else null
    }

    if (showCountryPicker) {
        val filteredCountries = remember(countrySearchQuery) {
            val q = countrySearchQuery.trim().lowercase()
            if (q.isBlank()) {
                CountryPhoneCatalog.allCountries
            } else {
                CountryPhoneCatalog.allCountries.filter {
                    it.name.lowercase().contains(q) ||
                        it.dialCode.contains(q) ||
                        it.isoCode.lowercase().contains(q)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCountryPicker = false },
            containerColor = cardDark,
            title = {
                Text("Selecciona tu país", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = countrySearchQuery,
                        onValueChange = { countrySearchQuery = it },
                        placeholder = { Text("Buscar país o código (+51)", color = TextSecondaryGray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = whatsappGreen) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(filteredCountries) { country ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCountry = country
                                        phone = phone.take(country.maxDigits)
                                        phoneError = null
                                        showCountryPicker = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${country.flag}  ${country.name}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${country.dialCode} (${country.maxDigits}d)",
                                    color = whatsappGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            HorizontalDivider(color = Color(0xFF2A3942), thickness = 0.5.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCountryPicker = false }) {
                    Text("Cerrar", color = whatsappGreen)
                }
            }
        )
    }

    val strength = remember(password) { AuthViewModel.calculatePasswordStrength(password) }
    val animatedStrengthScore by animateFloatAsState(targetValue = strength.score, label = "strength_score")
    val strengthColor by animateColorAsState(
        targetValue = when {
            password.isEmpty() -> TextSecondaryGray
            strength.score <= 0.35f -> ErrorRed
            strength.score <= 0.60f -> Color(0xFFFF9800)
            strength.score < 0.90f -> Color(0xFFFFD600)
            else -> whatsappGreen
        },
        label = "strength_color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Ingresa tus datos para registrarte",
                fontSize = 15.sp,
                color = TextSecondaryGray
            )

            Spacer(modifier = Modifier.height(18.dp))

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
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(14.dp))

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
                        Text("✓ @${username.trim().removePrefix("@")} está disponible", color = whatsappGreen)
                    } else {
                        Text("Identificador único para que otros te encuentren (ej: @carlos20)", color = TextSecondaryGray)
                    }
                },
                enabled = !isLoading,
                label = { Text("Nombre de usuario (@usuario)") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = whatsappGreen) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = null; submitError = null },
                isError = emailError != null,
                supportingText = emailError?.let { { Text(it, color = ErrorRed) } },
                enabled = !isLoading,
                label = { Text("Correo electrónico") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = whatsappGreen) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(14.dp))

            // COMBOBOX DE PAÍS (AUTODETECTADO POR IP) + LÍMITE ESTRICTO DE DÍGITOS NUMÉRICOS
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isLoading) { showCountryPicker = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardDark),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("País (detectado automáticamente)", color = TextSecondaryGray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${selectedCountry.flag}  ${selectedCountry.name} (${selectedCountry.dialCode})",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Cambiar país",
                        tint = whatsappGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { raw ->
                    phone = raw.filter { it.isDigit() }.take(selectedCountry.maxDigits)
                    phoneError = null
                    submitError = null
                },
                isError = phoneError != null,
                supportingText = {
                    if (phoneError != null) {
                        Text(phoneError!!, color = ErrorRed)
                    } else {
                        Text(
                            text = "${phone.length}/${selectedCountry.maxDigits} dígitos numéricos para ${selectedCountry.name} (${selectedCountry.dialCode})",
                            color = TextSecondaryGray
                        )
                    }
                },
                enabled = !isLoading,
                label = { Text("Número de celular (${selectedCountry.dialCode})") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = whatsappGreen) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(14.dp))

            // CAMPO DE CONTRASEÑA CON VISIBILIDAD Y MEDIDOR DESLIZANTE DE FUERZA
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; passwordError = null; submitError = null },
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it, color = ErrorRed) } },
                enabled = !isLoading,
                label = { Text("Contraseña segura") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = whatsappGreen) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Ocultar contraseña" else "Ver contraseña",
                            tint = TextSecondaryGray
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
            )

            Spacer(modifier = Modifier.height(8.dp))

            // BARRA DESLIZANTE INDICADORA DE FUERZA DE CONTRASEÑA
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardDark),
                border = BorderStroke(1.dp, Color(0xFF2A3942))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Seguridad de la contraseña:",
                            color = TextSecondaryGray,
                            fontSize = 12.sp
                        )
                        Text(
                            text = strength.label,
                            color = strengthColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = animatedStrengthScore,
                        onValueChange = {},
                        enabled = false,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            disabledThumbColor = strengthColor,
                            disabledActiveTrackColor = strengthColor,
                            disabledInactiveTrackColor = Color(0xFF2A3942)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    PasswordRequirementRow("Mínimo 8 caracteres", strength.hasMinLength, whatsappGreen)
                    PasswordRequirementRow("Al menos una letra mayúscula (A-Z)", strength.hasUppercase, whatsappGreen)
                    PasswordRequirementRow("Al menos un número (0-9)", strength.hasNumber, whatsappGreen)
                    PasswordRequirementRow("Un carácter especial (@, *, #, $, etc.)", strength.hasSpecialChar, whatsappGreen)
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Button(
                onClick = {
                    val cleanUsername = username.trim().removePrefix("@").lowercase()
                    nameError = if (fullName.isBlank()) "Ingresa tu nombre completo." else null
                    usernameError = AuthViewModel.validateUsername(username)
                        ?: if (isUsernameAvailableState == false) "El usuario @$cleanUsername ya existe. Elige otro." else null
                    emailError = AuthViewModel.validateEmail(email)
                    phoneError = AuthViewModel.validatePhoneForCountry(phone, selectedCountry)
                    passwordError = AuthViewModel.validatePassword(password)
                    if (nameError != null || usernameError != null || emailError != null || phoneError != null || passwordError != null) {
                        return@Button
                    }
                    authViewModel.registerManual(
                        RegistrationForm(
                            fullName = fullName.trim(),
                            username = cleanUsername,
                            email = email.trim(),
                            phone = "${selectedCountry.dialCode} ${phone.trim()}",
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PasswordRequirementRow(
    label: String,
    satisfied: Boolean,
    okColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (satisfied) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (satisfied) okColor else TextSecondaryGray,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (satisfied) Color.White else TextSecondaryGray,
            fontSize = 12.sp
        )
    }
}
