package com.alad.app.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alad.app.core.audio.AudioCaptureManager
import com.alad.app.core.audio.AudioPlayerManager
import com.alad.app.core.network.ALADWebSocketManager
import com.alad.app.core.network.StreamMode
import com.alad.app.core.network.TranscriptKind
import com.alad.app.core.shadow.ShadowController
import com.alad.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import kotlin.math.sqrt

class AudioDubbingForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "alad_dubbing_channel"
        private const val NOTIFICATION_ID = 101

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_AUDIO_SOURCE = "EXTRA_AUDIO_SOURCE"
        private const val TAG = "DubbingService"

        val isRunning = MutableStateFlow(false)
        val audioAmplitude = MutableStateFlow(0f)

        /** RMS below this (normalized 0..1) counts as silence for the quota saver. */
        private const val SILENCE_RMS = 0.012f
        /** Stop forwarding audio after this long of continuous silence. */
        private const val SILENCE_TIMEOUT_MS = 1200L
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var mediaProjection: MediaProjection? = null
    private var audioCaptureManager: AudioCaptureManager? = null
    private var audioPlayerManager: AudioPlayerManager? = null
    private var webSocketManager: ALADWebSocketManager? = null
    private var langObservationJob: Job? = null
    private var shadowController: ShadowController? = null
    private val sentenceBuffer get() = LiveSessionState.sentenceBuffer

    // Per-session feature flags (snapshot at start)
    private var fgsMicMode = false
    private var liteMode = false
    private var shadowMode = false
    private var shadowAutopause = false
    private var skipSilence = true
    private var useMic = false

    // Incremental transcript accumulators
    private val inputAcc = StringBuilder()
    private val outputAcc = StringBuilder()

    // Karaoke progress estimation (24 kHz mono 16-bit dubbed audio)
    private var karaokeAudioBytes = 0L
    private var karaokeWordCount = 0

    // Silence gating state
    private var silenceSince = 0L
    private var forwarding = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        shadowController = ShadowController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START -> {
                fgsMicMode = intent.getStringExtra(EXTRA_AUDIO_SOURCE) ==
                    UserPreferencesRepository.AUDIO_SOURCE_MIC
                startForegroundWithType(createNotification())

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

                startDubbing(resultCode, data)
            }
            ACTION_STOP -> {
                stopDubbing()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startDubbing(resultCode: Int, data: Intent?) {
        isRunning.value = true
        LiveSessionState.reset()
        sentenceBuffer.clear()
        inputAcc.clear()
        outputAcc.clear()

        if (resultCode != 0 && data != null) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        }

        serviceScope.launch {
            val repository = UserPreferencesRepository(applicationContext)

            val apiKey = repository.apiKeyFlow.first()
            val targetLang = repository.targetLangFlow.first()
            val volumeRatio = repository.volumeRatioFlow.first()
            liteMode = repository.liteModeFlow.first()
            shadowMode = repository.shadowModeFlow.first()
            shadowAutopause = repository.shadowAutopauseFlow.first()
            skipSilence = repository.skipSilenceFlow.first()
            useMic = fgsMicMode
            val subtitlesOn = repository.subtitlesEnabledFlow.first()

            val mode = if (liteMode) StreamMode.LITE else StreamMode.DUB
            webSocketManager = ALADWebSocketManager(OkHttpClient())

            if (!liteMode) {
                audioPlayerManager = AudioPlayerManager(applicationContext)
                audioPlayerManager?.start()
                audioPlayerManager?.setVolume(volumeRatio)
            }

            webSocketManager?.onStatusChanged = { status ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(applicationContext, "WS Status: $status", android.widget.Toast.LENGTH_LONG).show()
                }
            }

            webSocketManager?.onBinaryMessageReceived = { audioChunk ->
                audioPlayerManager?.playAudioData(audioChunk)
                onDubAudioPlayed(audioChunk.size)
            }

            webSocketManager?.onTranscriptReceived = { kind, text ->
                onTranscript(kind, text)
            }
            webSocketManager?.onTurnComplete = {
                finalizeSentence()
            }

            // sourceLang is no longer used for Bidi Setup
            webSocketManager?.connect(apiKey, targetLang, mode, enableTranscription = true)

            langObservationJob?.cancel()
            langObservationJob = serviceScope.launch {
                var firstEmit = true
                repository.targetLangFlow.collect { newLang ->
                    if (firstEmit) {
                        firstEmit = false
                    } else {
                        val currentKey = repository.apiKeyFlow.first()
                        webSocketManager?.disconnect()
                        webSocketManager?.connect(currentKey, newLang, mode, enableTranscription = true)
                    }
                }
            }

            audioCaptureManager = AudioCaptureManager()
            val appUid = applicationInfo.uid
            val onPcm: (ByteArray) -> Unit = { pcmData ->
                sentenceBuffer.push(pcmData)
                if (shouldForward(pcmData)) {
                    webSocketManager?.sendAudioData(pcmData)
                }
                updateAmplitude(pcmData)
            }

            if (useMic) {
                // Around-me mode: microphone, no screen-capture projection needed.
                audioCaptureManager?.startMicCapture(onPcm)
            } else {
                mediaProjection?.let { projection ->
                    audioCaptureManager?.startCapture(projection, appUid, onPcm)
                }
            }

            if (subtitlesOn) {
                startService(Intent(applicationContext, SubtitleOverlayService::class.java))
            }

            // Refresh the foreground notification now that the mode is known.
            startForegroundWithType(createNotification())
        }
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (fgsMicMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Quota saver: don't send audio while the source is silent/paused. */
    private fun shouldForward(pcmData: ByteArray): Boolean {
        if (!skipSilence) return true
        val rms = rmsNormalized(pcmData)
        val now = System.currentTimeMillis()
        return if (rms < SILENCE_RMS) {
            if (forwarding) {
                if (silenceSince == 0L) silenceSince = now
                if (now - silenceSince > SILENCE_TIMEOUT_MS) {
                    forwarding = false
                    Log.d(TAG, "Silence gate closed (quota saver)")
                }
            }
            false
        } else {
            if (!forwarding) Log.d(TAG, "Silence gate opened")
            silenceSince = 0L
            forwarding = true
            true
        }
    }

    private fun updateAmplitude(pcmData: ByteArray) {
        val normalized = rmsNormalized(pcmData)
        val current = audioAmplitude.value
        audioAmplitude.value = current * 0.5f + (normalized * 3f).coerceIn(0f, 1f) * 0.5f
    }

    private fun rmsNormalized(pcmData: ByteArray): Float {
        var sum = 0.0
        var n = 0
        for (i in pcmData.indices step 2) {
            if (i + 1 < pcmData.size) {
                val sample = (pcmData[i].toInt() and 0xFF) or (pcmData[i + 1].toInt() shl 8)
                val s = sample.toShort().toFloat()
                sum += s * s
                n++
            }
        }
        return if (n > 0) (sqrt(sum / n) / 32767f).toFloat() else 0f
    }

    private fun onTranscript(kind: TranscriptKind, text: String) {
        if (text.isBlank()) return
        when (kind) {
            TranscriptKind.INPUT -> {
                inputAcc.append(text)
                LiveSessionState.subtitleOriginal.value = inputAcc.toString()
            }
            TranscriptKind.OUTPUT -> {
                outputAcc.append(text)
                val full = outputAcc.toString()
                LiveSessionState.subtitleTranslated.value = full
                updateKaraoke(full)
                // Heuristic sentence boundary when the model doesn't send turnComplete.
                if (isSentenceEnd(full) && full.length > 12) {
                    finalizeSentence()
                }
            }
        }
    }

    private fun isSentenceEnd(text: String): Boolean {
        val t = text.trimEnd()
        return t.endsWith(".") || t.endsWith("!") || t.endsWith("?") ||
            t.endsWith("。") || t.endsWith("！") || t.endsWith("؟")
    }

    /** Estimate which translated words the dubbed voice has already spoken. */
    private fun onDubAudioPlayed(bytes: Int) {
        if (karaokeWordCount == 0) return
        karaokeAudioBytes += bytes
        // 24 kHz mono 16-bit -> 48000 bytes/sec; assume ~2.6 words/sec average speech.
        val spokenSec = karaokeAudioBytes / 48000.0
        val spoken = (spokenSec * 2.6).toInt().coerceIn(0, karaokeWordCount)
        LiveSessionState.karaokeSpoken.value = spoken
    }

    private fun updateKaraoke(translated: String) {
        val words = translated.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.size != karaokeWordCount) {
            karaokeWordCount = words.size
            karaokeAudioBytes = 0L
            LiveSessionState.karaokeWords.value = words
            LiveSessionState.karaokeSpoken.value = 0
        } else {
            LiveSessionState.karaokeWords.value = words
        }
    }

    private fun finalizeSentence() {
        val original = inputAcc.toString().trim()
        val translated = outputAcc.toString().trim()
        inputAcc.clear()
        outputAcc.clear()
        karaokeWordCount = 0
        karaokeAudioBytes = 0L
        if (translated.isEmpty()) return

        if (shadowMode) {
            LiveSessionState.shadowCard.value = ShadowCard(original, translated)
            if (shadowAutopause) {
                shadowController?.pauseMedia()
                LiveSessionState.shadowPaused.value = true
            }
        }
    }

    private fun stopDubbing() {
        isRunning.value = false
        LiveSessionState.reset()
        audioCaptureManager?.stopCapture()
        audioCaptureManager = null

        audioPlayerManager?.stop()
        audioPlayerManager = null

        webSocketManager?.disconnect()
        webSocketManager = null

        audioAmplitude.value = 0f
        sentenceBuffer.clear()

        langObservationJob?.cancel()
        langObservationJob = null

        mediaProjection?.stop()
        mediaProjection = null

        stopService(Intent(this, SubtitleOverlayService::class.java))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning.value) {
            stopDubbing()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Live Dubbing Service"
            val descriptionText = "Capturing and streaming audio for dubbing"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, com.alad.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AudioDubbingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 1, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (liteMode) "ALAD Lite Subtitles" else "ALAD Live Dubbing"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Connected and dubbing...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
