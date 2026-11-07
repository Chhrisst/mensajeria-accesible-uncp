package com.example.mensajeria.data.repository

import android.content.Context
import com.example.mensajeria.data.model.UserProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositorio de autenticación y perfiles de usuario accesibles para la UNCP.
 * Soporta persistencia permanente en SharedPreferences y búsqueda fonética tolerante.
 */
class AuthRepository(private val context: Context? = null) {

    companion object {
        private const val PREFS_NAME = "uncp_auth_prefs"
        private const val KEY_REGISTERED_USERS = "registered_users_list"
        private var sharedContext: Context? = null

        private val _users = mutableListOf(
            UserProfile(
                id = "u1",
                name = "Romell Rivera",
                email = "romell@gmail.com",
                phone = "987 654 321",
                dni = "72345678",
                code = "123",
                sidePattern = "LRLR",
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
                sidePattern = "LRLR",
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
                sidePattern = "LRLR",
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
                sidePattern = "LRLR",
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
                sidePattern = "LRLR",
                role = "Participante Experimental",
                description = "Perfil de evaluación y pruebas",
                hasDisability = true,
                visualCondition = "Ceguera total"
            )
        )
    }

    private val gson = Gson()

    init {
        if (context != null) {
            sharedContext = context.applicationContext
        }
        loadPersistedUsers()
    }

    private fun getEffectiveContext(): Context? = context ?: sharedContext

    private fun loadPersistedUsers() {
        val ctx = getEffectiveContext() ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_REGISTERED_USERS, null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<UserProfile>>() {}.type
                val savedList: List<UserProfile> = gson.fromJson(json, type)
                savedList.reversed().forEach { savedUser ->
                    if (_users.none { it.id == savedUser.id || it.name.equals(savedUser.name, ignoreCase = true) }) {
                        _users.add(0, savedUser)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun persistUser(user: UserProfile) {
        val ctx = getEffectiveContext() ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val customUsers = _users.filter { u -> u.id.length > 5 || !u.id.startsWith("u") }
            val json = gson.toJson(customUsers)
            prefs.edit().putString(KEY_REGISTERED_USERS, json).apply()
        } catch (_: Exception) {}
    }

    val predefinedUsers: List<UserProfile>
        get() = _users.toList()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    fun loginWithProfile(user: UserProfile) {
        _currentUser.value = user
    }

    private fun normalizeString(text: String): String {
        return text.trim().lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .replace("christian", "cristian")
            .replace("cristhian", "cristian")
            .replace(".", "")
            .replace(",", "")
    }

    fun findUserByQuery(query: String): UserProfile? {
        val cleanQuery = normalizeString(query)
        if (cleanQuery.isBlank()) return null

        val stopWords = setOf("soy", "mi", "nombre", "es", "cuenta", "de", "iniciar", "sesion", "con", "hola", "por", "favor", "el", "la", "usuario")
        val rawTokens = cleanQuery.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        val significantTokens = rawTokens.filter { !stopWords.contains(it) && it.length >= 3 }
        val effectiveTokens = if (significantTokens.isNotEmpty()) significantTokens else rawTokens

        return _users.firstOrNull { user ->
            val cleanUserName = normalizeString(user.name)
            val userTokens = cleanUserName.split(" ").filter { it.length >= 3 }

            // 1. Coincidencia directa de substring
            cleanUserName.contains(cleanQuery) ||
            cleanQuery.contains(cleanUserName) ||
            // 2. Coincidencia por tokens significativos (ej: "soy cristian" -> token "cristian" coincide con "christian beteta")
            (effectiveTokens.isNotEmpty() && effectiveTokens.any { qTok ->
                userTokens.any { uTok -> uTok == qTok || uTok.contains(qTok) || qTok.contains(uTok) }
            }) ||
            // 3. Coincidencia por DNI / Teléfono / Correo / Código
            user.code == query.trim() ||
            user.dni == query.trim() ||
            user.email.lowercase() == query.trim().lowercase() ||
            user.phone.replace(" ", "") == query.replace(" ", "")
        }
    }

    fun verifyPin(user: UserProfile, pin: String): Boolean {
        val clean = pin.trim().uppercase()
        return user.code.uppercase() == clean ||
                user.sidePattern.uppercase() == clean ||
                (user.code.uppercase() == "123" && (clean == "1234" || clean == "123")) ||
                (user.sidePattern.uppercase() == "LRL" && (clean == "LRLR" || clean == "LRL"))
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
            code = form.pin.ifBlank { "1234" },
            sidePattern = form.pin.ifBlank { "LRLR" },
            role = "Socio Activo UNCP",
            description = if (form.hasDisability) form.visualCondition else "Sin discapacidad",
            hasDisability = form.hasDisability,
            visualCondition = form.visualCondition
        )
        _users.add(0, newUser)
        _currentUser.value = newUser
        persistUser(newUser)
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

