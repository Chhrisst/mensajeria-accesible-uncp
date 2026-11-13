package com.example.mensajeria.data.repository

import android.content.Context
import com.example.mensajeria.data.model.UserProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
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
        private const val KEY_ACTIVE_USER = "active_session_user"
        private var sharedContext: Context? = null
        private val _users = mutableListOf<UserProfile>()
    }

    private val gson = Gson()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    init {
        if (context != null) {
            sharedContext = context.applicationContext
        }
        loadPersistedUsers()
        loadActiveSession()
    }

    private fun getEffectiveContext(): Context? = context ?: sharedContext

    private fun loadActiveSession() {
        val ctx = getEffectiveContext() ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_ACTIVE_USER, null)
            if (!json.isNullOrBlank()) {
                val savedUser = sanitizeUser(gson.fromJson(json, UserProfile::class.java))
                if (savedUser != null) {
                    _currentUser.value = savedUser
                }
            }
        } catch (_: Exception) {}
    }

    private fun saveActiveSession(user: UserProfile?) {
        val ctx = getEffectiveContext() ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (user == null || user.id.isBlank()) {
                prefs.edit().remove(KEY_ACTIVE_USER).apply()
            } else {
                prefs.edit().putString(KEY_ACTIVE_USER, gson.toJson(user)).apply()
            }
        } catch (_: Exception) {}
    }

    private fun loadPersistedUsers() {
        val ctx = getEffectiveContext() ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_REGISTERED_USERS, null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<UserProfile>>() {}.type
                val savedList: List<UserProfile?> = gson.fromJson(json, type) ?: emptyList()
                savedList.mapNotNull { sanitizeUser(it) }.reversed().forEach { savedUser ->
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

    /**
     * Gson ignora la nulabilidad de Kotlin: un JSON guardado por una versión anterior de la app (o dañado)
     * puede traer campos null en propiedades no anulables. Sin esta limpieza, findUserByQuery()
     * lanzaba NullPointerException (p. ej. en user.email.lowercase()) durante el login por voz.
     */
    @Suppress("USELESS_CAST")
    private fun sanitizeUser(raw: UserProfile?): UserProfile? {
        if (raw == null) return null
        val id = (raw.id as String?)?.takeIf { it.isNotBlank() } ?: return null
        val name = (raw.name as String?)?.takeIf { it.isNotBlank() } ?: return null
        val defaults = UserProfile(id = id, name = name)
        return UserProfile(
            id = id,
            name = name,
            username = (raw.username as String?) ?: defaults.username,
            email = (raw.email as String?) ?: defaults.email,
            phone = (raw.phone as String?) ?: defaults.phone,
            dni = (raw.dni as String?) ?: defaults.dni,
            birthDate = (raw.birthDate as String?) ?: defaults.birthDate,
            code = (raw.code as String?) ?: defaults.code,
            sidePattern = (raw.sidePattern as String?) ?: defaults.sidePattern,
            role = (raw.role as String?) ?: defaults.role,
            description = (raw.description as String?) ?: defaults.description,
            hasDisability = raw.hasDisability,
            visualCondition = (raw.visualCondition as String?) ?: defaults.visualCondition
        )
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

    fun loginWithProfile(user: UserProfile) {
        _currentUser.value = user
        saveActiveSession(user)
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
            // 2. Coincidencia por tokens significativos en el nombre
            (effectiveTokens.isNotEmpty() && effectiveTokens.any { qTok ->
                userTokens.any { uTok -> uTok == qTok || uTok.contains(qTok) || qTok.contains(uTok) }
            }) ||
            // 3. Coincidencia por DNI / Teléfono / Correo / Código / Username
            user.username.equals(query.trim().removePrefix("@"), ignoreCase = true) ||
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
            saveActiveSession(found)
        }
        return found
    }

    fun registerUser(form: com.example.mensajeria.data.model.RegistrationForm): UserProfile {
        val desc = if (form.hasDisability) "Con discapacidad" else "Sin discapacidad"
        val cleanBase = form.fullName.filter { it.isLetterOrDigit() }.lowercase().ifBlank { "usuario" }
        val generatedUsername = "${cleanBase}_${System.currentTimeMillis().toString().takeLast(4)}"
        val newUser = UserProfile(
            name = form.fullName.ifBlank { "Nuevo Socio" },
            username = generatedUsername,
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
        saveActiveSession(newUser)
        return newUser
    }

    suspend fun loginRemote(query: String, passwordOrPin: String): Result<UserProfile> {
        return try {
            val response = com.example.mensajeria.data.api.RetrofitClient.api.login(
                com.example.mensajeria.data.api.ApiLoginRequest(query, passwordOrPin)
            )
            val b = response.body()
            val remoteId = b?.id?.takeIf { it.isNotBlank() }
            if (response.isSuccessful && b != null && remoteId != null) {
                val desc = if (b.hasDisability) "Con discapacidad" else "Sin discapacidad"
                val profile = UserProfile(
                    id = remoteId,
                    name = b.name?.takeIf { it.isNotBlank() } ?: b.username ?: query.trim(),
                    username = b.username ?: "",
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
                saveActiveSession(profile)
                Result.success(profile)
            } else {
                val localUser = findUserByQuery(query)
                if (localUser != null && passwordOrPin.isNotBlank() && verifyPin(localUser, passwordOrPin)) {
                    _currentUser.value = localUser
                    saveActiveSession(localUser)
                    Result.success(localUser)
                } else {
                    Result.failure(Exception("Credenciales inválidas."))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val localUser = findUserByQuery(query)
            if (localUser != null && passwordOrPin.isNotBlank() && verifyPin(localUser, passwordOrPin)) {
                _currentUser.value = localUser
                saveActiveSession(localUser)
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
                val clean = form.fullName.filter { it.isLetterOrDigit() }.lowercase().ifBlank { "usuario" }
                val suffix = java.util.UUID.randomUUID().toString().substring(0, 6)
                "${clean}_${suffix}@messenger.pe"
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
            val b = response.body()
            val remoteId = b?.id?.takeIf { it.isNotBlank() }
            if (response.isSuccessful && b != null && remoteId != null) {
                val profile = UserProfile(
                    id = remoteId,
                    name = b.name?.takeIf { it.isNotBlank() } ?: form.fullName.ifBlank { "Nuevo Socio" },
                    username = b.username ?: "",
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
                saveActiveSession(profile)
                Result.success(profile)
            } else {
                val local = registerUser(form)
                Result.success(local)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val local = registerUser(form)
            Result.success(local)
        }
    }

    fun logout() {
        _currentUser.value = null
        saveActiveSession(null)
    }
}

