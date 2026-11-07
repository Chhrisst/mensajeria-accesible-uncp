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
                id = "11111111-1111-1111-1111-111111111111",
                name = "Christian Beteta",
                email = "christian@gmail.com",
                phone = "912 345 678",
                dni = "71234567",
                code = "123",
                sidePattern = "LRL",
                role = "Usuario",
                description = "Con discapacidad",
                hasDisability = true,
                visualCondition = "Con discapacidad"
            ),
            UserProfile(
                id = "22222222-2222-2222-2222-222222222222",
                name = "Romell Rivera",
                email = "romell@gmail.com",
                phone = "987 654 321",
                dni = "72345678",
                code = "123",
                sidePattern = "LRR",
                role = "Usuario",
                description = "Con discapacidad",
                hasDisability = true,
                visualCondition = "Con discapacidad"
            ),
            UserProfile(
                id = "33333333-3333-3333-3333-333333333333",
                name = "Miguel Retuerto",
                email = "miguel@gmail.com",
                phone = "998 877 665",
                dni = "73456789",
                code = "456",
                sidePattern = "LLR",
                role = "Usuario",
                description = "Con discapacidad",
                hasDisability = true,
                visualCondition = "Con discapacidad"
            ),
            UserProfile(
                id = "44444444-4444-4444-4444-444444444444",
                name = "Juan Perez García",
                email = "juan@gmail.com",
                phone = "987 654 321",
                dni = "74567890",
                code = "123",
                sidePattern = "RLR",
                role = "Usuario",
                description = "Con discapacidad",
                hasDisability = true,
                visualCondition = "Con discapacidad"
            ),
            UserProfile(
                id = "47bc3462-6f1c-45dc-a211-71bc7c1a8903",
                name = "Christian",
                email = "christian_beteta_15@hotmail.com",
                phone = "998292048",
                code = "123456",
                sidePattern = "LRL",
                role = "Usuario",
                description = "Sin discapacidad",
                hasDisability = false,
                visualCondition = "Sin discapacidad"
            ),
            UserProfile(
                id = "55555555-5555-5555-5555-555555555555",
                name = "Socio Invitado",
                email = "invitado@uncp.pe",
                phone = "900 000 000",
                code = "000",
                sidePattern = "LLL",
                role = "Usuario",
                description = "Con discapacidad",
                hasDisability = true,
                visualCondition = "Con discapacidad"
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
                    val cleanDesc = if (savedUser.hasDisability) "Con discapacidad" else "Sin discapacidad"
                    val standardized = savedUser.copy(
                        role = "Usuario",
                        description = cleanDesc,
                        visualCondition = cleanDesc
                    )
                    if (_users.none { it.id == standardized.id || it.name.equals(standardized.name, ignoreCase = true) }) {
                        _users.add(0, standardized)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun persistUser(user: UserProfile) {
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
        val desc = if (form.hasDisability) "Con discapacidad" else "Sin discapacidad"
        val newUser = UserProfile(
            name = form.fullName.ifBlank { "Nuevo Socio" },
            email = form.email,
            phone = form.phone,
            dni = form.dni,
            birthDate = form.birthDate,
            code = form.pin.ifBlank { "123" },
            sidePattern = form.pin.ifBlank { "LRL" },
            role = "Usuario",
            description = desc,
            hasDisability = form.hasDisability,
            visualCondition = desc
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
                val desc = if (b.hasDisability) "Con discapacidad" else "Sin discapacidad"
                val profile = UserProfile(
                    id = b.id,
                    name = b.name,
                    email = b.email ?: "",
                    phone = b.phone ?: "",
                    dni = b.dni ?: "",
                    code = b.code ?: passwordOrPin,
                    sidePattern = b.sidePattern ?: passwordOrPin,
                    role = "Usuario",
                    description = desc,
                    hasDisability = b.hasDisability,
                    visualCondition = desc
                )
                _currentUser.value = profile
                persistUser(profile)
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
            val emailToSend = if (!form.email.isNullOrBlank()) {
                form.email.trim().lowercase()
            } else {
                val clean = form.fullName.filter { it.isLetterOrDigit() }.lowercase().ifBlank { "socio" }
                val suffix = java.util.UUID.randomUUID().toString().substring(0, 6)
                "${clean}_${suffix}@uncp.pe"
            }
            val desc = if (form.hasDisability) "Con discapacidad" else "Sin discapacidad"
            val req = com.example.mensajeria.data.api.ApiRegisterRequest(
                fullName = form.fullName,
                email = emailToSend,
                phone = form.phone,
                dni = form.dni,
                birthDate = form.birthDate,
                pin = form.pin.ifBlank { "123" },
                sidePattern = form.pin.ifBlank { "LRL" },
                hasDisability = form.hasDisability,
                visualCondition = desc
            )
            val response = com.example.mensajeria.data.api.RetrofitClient.api.register(req)
            if (response.isSuccessful && response.body() != null) {
                val b = response.body()!!
                val profile = UserProfile(
                    id = b.id,
                    name = b.name,
                    email = b.email ?: emailToSend,
                    phone = b.phone ?: "",
                    dni = b.dni ?: "",
                    code = b.code ?: form.pin,
                    sidePattern = b.sidePattern ?: form.pin,
                    role = "Usuario",
                    description = desc,
                    hasDisability = b.hasDisability,
                    visualCondition = desc
                )
                _users.add(0, profile)
                _currentUser.value = profile
                persistUser(profile)
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

