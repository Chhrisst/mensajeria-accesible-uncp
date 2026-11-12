package com.example.mensajeria.data.api

import android.util.Log
import com.example.mensajeria.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val TAG = "RetrofitClient"
    private const val EMULATOR_FALLBACK_URL = "http://10.0.2.2:3000/"

    // Base URL inyectada de forma segura desde local.properties mediante BuildConfig.
    // Se valida aquí: una URL mal escrita (sin "http://", con comillas extra, etc.) haría que
    // Retrofit lance IllegalArgumentException en cada llamada y la app quedaría "sin red" en silencio.
    private val DEFAULT_BASE_URL: String = run {
        val configured = BuildConfig.BACKEND_BASE_URL.trim().trim('"')
        val normalized = if (configured.isBlank() || configured.endsWith("/")) configured else "$configured/"
        when {
            normalized.isBlank() -> {
                Log.e(TAG, "BACKEND_BASE_URL vacío en local.properties. Usando $EMULATOR_FALLBACK_URL (solo emulador).")
                EMULATOR_FALLBACK_URL
            }
            normalized.toHttpUrlOrNull() == null -> {
                Log.e(TAG, "BACKEND_BASE_URL inválido. Usando $EMULATOR_FALLBACK_URL (solo emulador).")
                EMULATOR_FALLBACK_URL
            }
            else -> normalized
        }
    }

    // Solo lectura: `api` se construye una única vez (lazy), cambiarla después no tendría efecto.
    val baseUrl: String = DEFAULT_BASE_URL

    // En release NO se registra el cuerpo: contiene PINs, contraseñas y mensajes privados.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            // Tope duro por llamada completa (incluye reintentos): evita spinners/estados colgados
            // en redes móviles inestables.
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
