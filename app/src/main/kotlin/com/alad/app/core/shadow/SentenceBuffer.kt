package com.alad.app.core.shadow

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import java.util.ArrayDeque

/**
 * Ring buffer of recently captured input audio (16 kHz mono PCM-16) so the
 * shadowing overlay can replay the last sentence at normal or slow speed.
 */
class SentenceBuffer(
    private val sampleRate: Int = 16000,
    private val maxSeconds: Int = 45
) {
    private val maxBytes = sampleRate * 2 * maxSeconds
    private val chunks = ArrayDeque<ByteArray>()
    private var bufferedBytes = 0

    @Synchronized
    fun push(pcm: ByteArray) {
        chunks.addLast(pcm)
        bufferedBytes += pcm.size
        while (bufferedBytes > maxBytes && chunks.isNotEmpty()) {
            bufferedBytes -= chunks.removeFirst().size
        }
    }

    /** Last [seconds] of audio as one PCM blob, or null when empty. */
    @Synchronized
    fun snapshot(seconds: Int): ByteArray? {
        if (chunks.isEmpty()) return null
        val want = (sampleRate * 2 * seconds).coerceAtMost(bufferedBytes)
        val out = ByteArray(want)
        var pos = want
        val it = chunks.descendingIterator()
        while (it.hasNext() && pos > 0) {
            val c = it.next()
            val take = minOf(c.size, pos)
            System.arraycopy(c, c.size - take, out, pos - take, take)
            pos -= take
        }
        return out
    }

    @Synchronized
    fun clear() {
        chunks.clear()
        bufferedBytes = 0
    }
}

/** One-shot player for sentence replay (normal / 0.5x slow). */
class SentenceReplayPlayer {
    companion object {
        private const val TAG = "SentenceReplayPlayer"
    }

    private var track: AudioTrack? = null

    fun play(pcm16kMono: ByteArray, slow: Boolean) {
        stop()
        try {
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(16000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                pcm16kMono.size.coerceAtLeast(4096),
                AudioTrack.MODE_STATIC,
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && slow) {
                track.playbackParams = PlaybackParams().setSpeed(0.5f)
            }
            track.write(pcm16kMono, 0, pcm16kMono.size)
            track.play()
            this.track = track
        } catch (e: Exception) {
            Log.w(TAG, "replay failed", e)
        }
    }

    fun stop() {
        try {
            track?.stop()
            track?.release()
        } catch (_: Exception) { }
        track = null
    }
}
