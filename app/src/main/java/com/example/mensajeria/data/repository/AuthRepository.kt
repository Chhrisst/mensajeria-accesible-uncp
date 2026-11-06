package com.example.mensajeria.data.repository

import com.example.mensajeria.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositorio de autenticación y perfiles de usuario accesibles para la UNCP.
 */
class AuthRepository {


    private val _users = mutableListOf(
        UserProfile(
            id = "u1",
            name = "Romell Rivera",
            email = "romell@gmail.com",
            phone = "987 654 321",
            dni = "72345678",
            code = "123",
            role = "Socio Activo UNCP",
            description = "Socio Activo UNCP",
            hasDisability = true,
            visualCondition = "Baja visión"
        ),
        UserProfile(
            id = "u2",
            name = "Christian Beteta",
            email = "christian@gmail.com",
            phone = "912 345 678",
            dni = "71234567",
            code = "123",
            role = "Socio UNCP",
            description = "Taller de Tecnologías Adaptadas",
            hasDisability = true,
            visualCondition = "Baja visión"
        ),
        UserProfile(
            id = "u3",
            name = "Miguel Retuerto",
            email = "miguel@gmail.com",
            phone = "998 877 665",
            dni = "73456789",
            code = "456",
            role = "Socio UNCP",
            description = "Comité de Comunicaciones",
            hasDisability = true,
            visualCondition = "Ceguera total"
        ),
        UserProfile(
            id = "u4",
            name = "Juan Perez García",
            email = "juan@gmail.com",
            phone = "987 654 321",
            dni = "74567890",
            code = "123",
            role = "Socio UNCP",
            description = "Usuario Registrado",
            hasDisability = true,
            visualCondition = "Ceguera total"
        ),
        UserProfile(
            id = "u5",
            name = "Socio Invitado",
            email = "invitado@uncp.pe",
            phone = "900 000 000",
            code = "000",
            role = "Participante Experimental",
            description = "Perfil de evaluación y pruebas",
            hasDisability = true,
            visualCondition = "Ceguera total"
        )
    )

    val predefinedUsers: List<UserProfile>
        get() = _users.toList()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    fun loginWithProfile(user: UserProfile) {
        _currentUser.value = user
    }

    fun findUserByQuery(query: String): UserProfile? {
        val clean = query.trim().lowercase()
        return _users.firstOrNull { user ->
            val uName = user.name.lowercase()
            uName.contains(clean) ||
            clean.contains(uName) ||
            (clean.contains("romell") && uName.contains("romell")) ||
            (clean.contains("christian") && uName.contains("christian")) ||
            (clean.contains("miguel") && uName.contains("miguel")) ||
            (clean.contains("juan") && uName.contains("juan")) ||
            (clean.contains("invitado") && uName.contains("invitado")) ||
            user.code == clean ||
            user.email.lowercase() == clean ||
            user.phone.replace(" ", "") == clean.replace(" ", "")
        }
    }

    fun verifyPin(user: UserProfile, pin: String): Boolean {
        val clean = pin.trim().uppercase()
        return user.code.uppercase() == clean || user.sidePattern.uppercase() == clean
    }

    fun loginByInput(input: String): UserProfile? {
        val found = findUserByQuery(input)
        if (found != null) {
            _currentUser.value = found
        }
        return found
    }

    fun registerUser(form: com.example.mensajeria.data.model.RegistrationForm): UserProfile {
        val newUser = UserProfile(
            name = form.fullName.ifBlank { "Nuevo Socio" },
            email = form.email,
            phone = form.phone,
            dni = form.dni,
            birthDate = form.birthDate,
            code = form.pin.ifBlank { "123" },
            role = "Socio Activo UNCP",
            description = if (form.hasDisability) form.visualCondition else "Sin discapacidad",
            hasDisability = form.hasDisability,
            visualCondition = form.visualCondition
        )
        _users.add(0, newUser)
        _currentUser.value = newUser
        return newUser
    }

    suspend fun loginRemote(query: String, passwordOrPin: String): Result<UserProfile> {
        return try {
            val response = com.example.mensajeria.data.api.RetrofitClient.api.login(
                com.example.mensajeria.data.api.ApiLoginRequest(query, passwordOrPin)
            )
            if (response.isSuccessful && response.body() != null) {
                val b = response.body()!!
                val profile = UserProfile(
                    id = b.id,
                    name = b.name,
                    email = b.email,
                    phone = b.phone ?: "",
                    dni = b.dni ?: "",
                    code = b.code,
                    role = b.role,
                    description = b.description ?: "",
                    hasDisability = b.hasDisability,
                    visualCondition = b.visualCondition
                )
                _currentUser.value = profile
                Result.success(profile)
            } else {
                val localUser = findUserByQuery(query)
                if (localUser != null && (passwordOrPin.isBlank() || verifyPin(localUser, passwordOrPin))) {
                    _currentUser.value = localUser
                    Result.success(localUser)
                } else {
                    Result.failure(Exception("Credenciales inválidas."))
                }
            }
        } catch (e: Exception) {
            val localUser = findUserByQuery(query)
            if (localUser != null && (passwordOrPin.isBlank() || verifyPin(localUser, passwordOrPin))) {
                _currentUser.value = localUser
                Result.success(localUser)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun registerRemote(form: com.example.mensajeria.data.model.RegistrationForm): Result<UserProfile> {
        return try {
            val req = com.example.mensajeria.data.api.ApiRegisterRequest(
                fullName = form.fullName,
                email = form.email,
                phone = form.phone,
                dni = form.dni,
                birthDate = form.birthDate,
                pin = form.pin.ifBlank { "123" },
                sidePattern = "LRL",
                hasDisability = form.hasDisability,
                visualCondition = form.visualCondition
            )
            val response = com.example.mensajeria.data.api.RetrofitClient.api.register(req)
            if (response.isSuccessful && response.body() != null) {
                val b = response.body()!!
                val profile = UserProfile(
                    id = b.id,
                    name = b.name,
                    email = b.email,
                    phone = b.phone ?: "",
                    dni = b.dni ?: "",
                    code = b.code,
                    role = b.role,
                    description = b.description ?: "",
                    hasDisability = b.hasDisability,
                    visualCondition = b.visualCondition
                )
                _users.add(0, profile)
                _currentUser.value = profile
                Result.success(profile)
            } else {
                val local = registerUser(form)
                Result.success(local)
            }
        } catch (e: Exception) {
            val local = registerUser(form)
            Result.success(local)
        }
    }

    fun logout() {
        _currentUser.value = null
    }
}

