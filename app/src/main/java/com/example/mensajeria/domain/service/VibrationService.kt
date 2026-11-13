package com.example.mensajeria.domain.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Servicio de retroalimentación háptica (vibración) para personas invidentes.
 */
class VibrationService(context: Context) {

    companion object {
        @Volatile
        private var instance: VibrationService? = null

        fun getInstance(context: Context): VibrationService {
            return instance ?: synchronized(this) {
                instance ?: VibrationService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Vibración táctil para confirmación al pulsar cualquier botón.
     */
    fun vibrateClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                vibrator?.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {
                vibrate(45L)
            }
        } else {
            vibrate(45L)
        }
    }

    fun vibrateStartListening() {
        // Vibración corta para indicar inicio de captura de micrófono
        vibrate(60)
    }

    /**
     * Vibración táctil distintiva al deslizar el dedo y pasar sobre cualquier botón o contenido.
     */
    fun vibrateHover() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                vibrator?.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {
                vibrate(30L)
            }
        } else {
            vibrate(30L)
        }
    }

    /**
     * Vibración para toque en el lado izquierdo.
     */
    fun vibrateLeftTap() {
        vibrate(35)
    }

    /**
     * Vibración para toque en el lado derecho (doble pulso corto).
     */
    fun vibrateRightTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 25, 30, 35),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 25, 30, 35), -1)
        }
    }

    /**
     * Vibración distintiva al pulsar ambos lados simultáneamente para seleccionar.
     */
    fun vibrateBothSidesSelection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 50, 40, 50, 40, 70),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 50, 40, 50, 40, 70), -1)
        }
    }

    fun vibrateSuccess() {
        // Doble pulso indicando éxito o confirmación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 40, 50, 60),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 40, 50, 60), -1)
        }
    }

    fun vibrateError() {
        // Pulso largo indicando error o cancelación
        vibrate(180)
    }

    private fun vibrate(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(durationMs)
        }
    }
}
