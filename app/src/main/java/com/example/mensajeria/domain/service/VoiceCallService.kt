package com.example.mensajeria.domain.service

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.example.mensajeria.data.api.RetrofitClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class CallSignalType {
    INVITE,
    RINGING,
    ACCEPT,
    REJECT,
    END,
    AUDIO,
    PRESENCE,
    OFFLINE
}

data class VoiceCallSignal(
    val type: CallSignalType,
    val callId: String,
    val callerId: String,
    val callerName: String,
    val timestampMs: Long
)

data class DecryptedCallStreamItem(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val isFromMe: Boolean,
    val content: String,
    val audioBase64: String,
    val createdAt: String
)

/**
 * Codificador/decodificador puro de señales CALL_STREAM para uso compartido entre runtime y pruebas unitarias.
 */
object CallSignalParser {
    private const val PREFIX = "CALL_SIGNAL|"
    private const val LEGACY_INVITE_PREFIX = "CALL_INVITE:"

    fun encode(signal: VoiceCallSignal): String {
        val safeName = signal.callerName.replace("|", " ").trim().ifBlank { "Usuario" }
        return "$PREFIX${signal.type.name}|${signal.callId}|${signal.callerId}|$safeName|${signal.timestampMs}"
    }

    fun parse(rawContent: String): VoiceCallSignal? {
        val trimmed = rawContent.trim()
        if (trimmed.startsWith(PREFIX)) {
            val parts = trimmed.split("|")
            if (parts.size < 6) return null
            val type = try {
                CallSignalType.valueOf(parts[1].trim().uppercase())
            } catch (_: Exception) {
                return null
            }
            val callId = parts[2].trim()
            val callerId = parts[3].trim()
            val callerName = parts[4].trim().ifBlank { "Usuario" }
            val ts = parts[5].trim().toLongOrNull() ?: return null
            if (callId.isBlank()) return null
            return VoiceCallSignal(
                type = type,
                callId = callId,
                callerId = callerId,
                callerName = callerName,
                timestampMs = ts
            )
        }
        if (trimmed.startsWith(LEGACY_INVITE_PREFIX)) {
            val ts = trimmed.removePrefix(LEGACY_INVITE_PREFIX).trim().toLongOrNull() ?: 0L
            if (ts <= 0L) return null
            return VoiceCallSignal(
                type = CallSignalType.INVITE,
                callId = "legacy_$ts",
                callerId = "",
                callerName = "Contacto",
                timestampMs = ts
            )
        }
        return null
    }

    fun isCallControlContent(rawContent: String): Boolean {
        val t = rawContent.trim()
        return t.startsWith(PREFIX) || t.startsWith(LEGACY_INVITE_PREFIX) || t.startsWith("CALL_")
    }

    fun hasCallTerminated(items: List<DecryptedCallStreamItem>, callId: String): Boolean {
        if (callId.isBlank()) return false
        return items.any { item ->
            val sig = parse(item.content) ?: return@any false
            sig.callId == callId && (sig.type == CallSignalType.END || sig.type == CallSignalType.REJECT)
        }
    }

    fun isCallAccepted(items: List<DecryptedCallStreamItem>, callId: String): Boolean {
        if (callId.isBlank() || hasCallTerminated(items, callId)) return false
        return items.any { item ->
            val sig = parse(item.content) ?: return@any false
            sig.callId == callId && sig.type == CallSignalType.ACCEPT
        }
    }

    fun isCallRejected(items: List<DecryptedCallStreamItem>, callId: String): Boolean {
        if (callId.isBlank()) return false
        return items.any { item ->
            val sig = parse(item.content) ?: return@any false
            sig.callId == callId && sig.type == CallSignalType.REJECT
        }
    }

    /**
     * Busca una invitación de llamada entrante reciente y aún activa (sin ACCEPT, REJECT ni END).
     */
    fun findPendingIncomingInvite(
        items: List<DecryptedCallStreamItem>,
        currentUserId: String,
        maxAgeMs: Long = 90_000L,
        nowMs: Long = System.currentTimeMillis()
    ): VoiceCallSignal? {
        val parsed = items.mapNotNull { item ->
            val sig = parse(item.content) ?: return@mapNotNull null
            item to sig
        }
        val finishedOrAcceptedIds = parsed
            .filter { (_, sig) ->
                sig.type == CallSignalType.ACCEPT ||
                    sig.type == CallSignalType.REJECT ||
                    sig.type == CallSignalType.END
            }
            .mapTo(HashSet()) { (_, sig) -> sig.callId }

        return parsed
            .filter { (item, sig) ->
                sig.type == CallSignalType.INVITE &&
                    !item.isFromMe &&
                    (sig.callerId.isBlank() || sig.callerId != currentUserId) &&
                    sig.callId !in finishedOrAcceptedIds &&
                    (nowMs - sig.timestampMs) in -120_000L..maxAgeMs
            }
            .maxByOrNull { (_, sig) -> sig.timestampMs }
            ?.second
    }

    /**
     * Evalúa el estado de presencia del contacto a partir de sus señales entrantes (!item.isFromMe).
     * Devuelve Pair(isOnline, latestTimestampMsOrNull).
     */
    fun computeContactPresence(
        items: List<DecryptedCallStreamItem>,
        maxOnlineAgeMs: Long = 180_000L,
        nowMs: Long = System.currentTimeMillis()
    ): Pair<Boolean, Long?> {
        val latestIncomingSignal = items
            .asSequence()
            .filter { !it.isFromMe }
            .mapNotNull { parse(it.content) }
            .maxByOrNull { it.timestampMs }
            ?: return false to null

        if (latestIncomingSignal.type == CallSignalType.OFFLINE) {
            return false to latestIncomingSignal.timestampMs
        }
        val ageMs = nowMs - latestIncomingSignal.timestampMs
        val isOnline = ageMs in -120_000L..maxOnlineAgeMs
        return isOnline to latestIncomingSignal.timestampMs
    }
}

interface VoiceCallListener {
    fun onIncomingInvite(callId: String, callerId: String, callerName: String)
    fun onRemoteRinging(callId: String, fromUserId: String)
    fun onRemoteAccepted(callId: String, fromUserId: String)
    fun onRemoteRejected(callId: String, fromUserId: String)
    fun onRemoteEnded(callId: String, fromUserId: String)
    fun onRemoteAudioChunk(callId: String, fromUserId: String, audioBase64: String)
    fun onRemoteHeartbeat(callId: String, fromUserId: String) {}
    fun onPeerOffline(callId: String, targetId: String) {}
}

/**
 * Servicio híbrido de llamadas de voz en tiempo real:
 * - Señalización instantánea y transmisión de audio PCM mediante WebSocket (/api/calls/ws/{user_id}).
 * - Compatibilidad automática con canal HTTP CALL_STREAM cifrado E2EE como respaldo.
 * - Motor full-duplex con AudioRecord + AudioTrack a 8 kHz Mono PCM 16-bit y cancelación de eco.
 */
class VoiceCallService(private val context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val wsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var boundUserId: String = ""
    @Volatile private var wsConnected: Boolean = false
    @Volatile private var isConnecting: Boolean = false
    @Volatile private var shouldStayConnected: Boolean = false
    @Volatile private var wsUnsupportedOnServer: Boolean = false

    private val reconnectRunnable = Runnable {
        val uid = boundUserId
        if (shouldStayConnected && uid.isNotBlank() && !wsConnected && !wsUnsupportedOnServer && !isConnecting) {
            openSocketInternal(uid)
        }
    }

    @Volatile var listener: VoiceCallListener? = null

    private val isStreaming = AtomicBoolean(false)
    @Volatile private var isMuted: Boolean = false
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private val playbackQueue = LinkedBlockingQueue<ByteArray>(24)

    fun isWebSocketConnected(): Boolean = wsConnected

    fun isNetworkConnected(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun connectWebSocket(userId: String) {
        val cleanId = userId.trim()
        if (cleanId.isBlank() || wsUnsupportedOnServer) return
        if (boundUserId == cleanId && (wsConnected || isConnecting) && webSocket != null) return

        boundUserId = cleanId
        shouldStayConnected = true
        mainHandler.removeCallbacks(reconnectRunnable)
        openSocketInternal(cleanId)
    }

    private fun openSocketInternal(userId: String) {
        if (wsUnsupportedOnServer) return
        isConnecting = true
        mainHandler.removeCallbacks(reconnectRunnable)
        try {
            webSocket?.close(1000, "Reconnecting")
        } catch (_: Exception) {}
        webSocket = null
        wsConnected = false

        val httpBase = RetrofitClient.baseUrl.trimEnd('/')
        val wsBase = when {
            httpBase.startsWith("https://", ignoreCase = true) -> "wss://" + httpBase.substring(8)
            httpBase.startsWith("http://", ignoreCase = true) -> "ws://" + httpBase.substring(7)
            else -> "ws://$httpBase"
        }
        val url = "$wsBase/api/calls/ws/$userId"
        val request = Request.Builder().url(url).build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnecting = false
                wsConnected = true
                Log.d(TAG, "WebSocket de llamadas conectado para $userId")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingWsJson(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                isConnecting = false
                wsConnected = false
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isConnecting = false
                wsConnected = false
                scheduleReconnectIfNeeded(userId)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                wsConnected = false
                val code = response?.code
                if (code == 403 || code == 404 || t.message?.contains("403") == true) {
                    wsUnsupportedOnServer = true
                    Log.i(TAG, "Servidor sin endpoint WS /api/calls/ws ($code); se usará canal HTTP CALL_STREAM.")
                    return
                }
                Log.w(TAG, "WebSocket de llamadas desconectado (${t.message})")
                scheduleReconnectIfNeeded(userId)
            }
        })
    }

    private fun scheduleReconnectIfNeeded(userId: String) {
        if (!shouldStayConnected || boundUserId != userId || wsUnsupportedOnServer) return
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, 4000L)
    }

    fun disconnectWebSocket() {
        shouldStayConnected = false
        boundUserId = ""
        wsConnected = false
        isConnecting = false
        mainHandler.removeCallbacks(reconnectRunnable)
        try {
            webSocket?.close(1000, "Session closed")
        } catch (_: Exception) {}
        webSocket = null
    }

    private fun handleIncomingWsJson(raw: String) {
        try {
            val obj = gson.fromJson(raw, JsonObject::class.java) ?: return
            val type = obj.get("type")?.asString ?: return
            val callId = obj.get("call_id")?.asString ?: ""
            val fromId = obj.get("caller_id")?.asString ?: obj.get("from_id")?.asString ?: ""
            val callerName = obj.get("caller_name")?.asString ?: "Contacto"
            val audioB64 = obj.get("audio")?.asString ?: ""

            mainHandler.post {
                val cb = listener ?: return@post
                when (type) {
                    "CALL_INVITE" -> cb.onIncomingInvite(callId, fromId, callerName)
                    "CALL_RINGING" -> cb.onRemoteRinging(callId, fromId)
                    "CALL_ACCEPT" -> cb.onRemoteAccepted(callId, fromId)
                    "CALL_REJECT" -> cb.onRemoteRejected(callId, fromId)
                    "CALL_END" -> cb.onRemoteEnded(callId, fromId)
                    "CALL_AUDIO" -> if (audioB64.isNotBlank()) cb.onRemoteAudioChunk(callId, fromId, audioB64)
                    "CALL_PRESENCE", "CALL_HEARTBEAT" -> cb.onRemoteHeartbeat(callId, fromId)
                    "WS_TARGET_OFFLINE" -> {
                        val targetId = obj.get("target_id")?.asString ?: fromId
                        cb.onPeerOffline(callId, targetId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Paquete WS inválido: ${e.message}")
        }
    }

    fun sendWebSocketSignal(
        type: String,
        callId: String,
        targetId: String,
        callerId: String,
        callerName: String,
        audioBase64: String? = null
    ): Boolean {
        val ws = webSocket ?: return false
        if (!wsConnected || targetId.isBlank()) return false
        return try {
            val payload = JsonObject().apply {
                addProperty("type", type)
                addProperty("call_id", callId)
                addProperty("target_id", targetId)
                addProperty("caller_id", callerId)
                addProperty("from_id", callerId)
                addProperty("caller_name", callerName)
                if (!audioBase64.isNullOrBlank()) {
                    addProperty("audio", audioBase64)
                }
            }
            ws.send(gson.toJson(payload))
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun startAudioStreaming(
        callId: String,
        targetId: String,
        callerId: String,
        callerName: String,
        speakerphoneOn: Boolean,
        onSendFallbackChunk: (String) -> Unit
    ) {
        if (isStreaming.getAndSet(true)) return
        playbackQueue.clear()

        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = speakerphoneOn
        } catch (_: Exception) {}

        startPlaybackWorker()
        startCaptureWorker(callId, targetId, callerId, callerName, onSendFallbackChunk)
    }

    private fun startPlaybackWorker() {
        val minOutBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_BYTES_WS * 2)

        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minOutBuf)
                .build()
            track.play()
            audioTrack = track
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo inicializar AudioTrack de llamada: ${e.message}")
        }

        playbackThread = Thread {
            while (isStreaming.get()) {
                try {
                    val chunk = playbackQueue.poll(250, TimeUnit.MILLISECONDS) ?: continue
                    val track = audioTrack
                    if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.write(chunk, 0, chunk.size)
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {}
            }
        }.apply {
            name = "VoiceCallPlayback"
            start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureWorker(
        callId: String,
        targetId: String,
        callerId: String,
        callerName: String,
        onSendFallbackChunk: (String) -> Unit
    ) {
        captureThread = Thread {
            val minInBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minInBuf <= 0) {
                Log.w(TAG, "AudioRecord no soportado en este dispositivo")
                return@Thread
            }

            val recBufSize = maxOf(minInBuf * 2, FRAME_BYTES_FALLBACK * 2)
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recBufSize
                )
            } catch (e: Exception) {
                Log.w(TAG, "Sin permiso o acceso a AudioRecord en llamada: ${e.message}")
                null
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                try { recorder?.release() } catch (_: Exception) {}
                return@Thread
            }

            audioRecord = recorder
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
                }
            } catch (_: Exception) {}

            try {
                recorder.startRecording()
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al iniciar captura de micrófono en llamada: ${e.message}")
                return@Thread
            }

            val wsFrame = ByteArray(FRAME_BYTES_WS)
            val httpAccum = java.io.ByteArrayOutputStream(FRAME_BYTES_FALLBACK + FRAME_BYTES_WS)

            while (isStreaming.get()) {
                val read = try {
                    recorder.read(wsFrame, 0, wsFrame.size)
                } catch (_: Exception) {
                    -1
                }
                if (read <= 0) continue
                if (isMuted) {
                    httpAccum.reset()
                    continue
                }

                // Verificar energía mínima para no saturar con silencio absoluto en fallback HTTP
                val encodedWs = Base64.encodeToString(wsFrame, 0, read, Base64.NO_WRAP)
                val sentByWs = sendWebSocketSignal(
                    type = "CALL_AUDIO",
                    callId = callId,
                    targetId = targetId,
                    callerId = callerId,
                    callerName = callerName,
                    audioBase64 = encodedWs
                )
                if (!sentByWs) {
                    httpAccum.write(wsFrame, 0, read)
                    if (httpAccum.size() >= FRAME_BYTES_FALLBACK) {
                        val batch = httpAccum.toByteArray()
                        httpAccum.reset()
                        val b64Batch = Base64.encodeToString(batch, Base64.NO_WRAP)
                        onSendFallbackChunk(b64Batch)
                    }
                } else {
                    httpAccum.reset()
                }
            }
        }.apply {
            name = "VoiceCallCapture"
            start()
        }
    }

    fun playRemotePcmBase64(audioBase64: String) {
        if (!isStreaming.get() || audioBase64.isBlank()) return
        try {
            val pcm = Base64.decode(audioBase64, Base64.DEFAULT)
            if (pcm.isEmpty()) return
            if (!playbackQueue.offer(pcm)) {
                playbackQueue.poll()
                playbackQueue.offer(pcm)
            }
        } catch (_: Exception) {}
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun setSpeakerphone(enabled: Boolean) {
        try {
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = enabled
        } catch (_: Exception) {}
    }

    fun stopAudioStreaming() {
        isStreaming.set(false)
        playbackQueue.clear()

        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null
        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
        } catch (_: Exception) {}
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        captureThread?.interrupt()
        captureThread = null
        playbackThread?.interrupt()
        playbackThread = null

        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = false
        } catch (_: Exception) {}
    }

    fun release() {
        stopAudioStreaming()
        disconnectWebSocket()
    }

    companion object {
        private const val TAG = "VoiceCallService"
        private const val SAMPLE_RATE = 8000
        private const val FRAME_BYTES_WS = 5600       // ~350 ms a 8kHz PCM16 Mono
        private const val FRAME_BYTES_FALLBACK = 10400 // ~650 ms para respaldo HTTP
    }
}
