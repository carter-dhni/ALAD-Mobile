package com.alad.app.core.shadow

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/**
 * Pauses / resumes the currently playing media app by dispatching media-key
 * events, the same mechanism headset buttons use. Used by shadowing mode's
 * auto-pause: after each translated sentence the video pauses so the learner
 * can repeat the sentence out loud.
 */
class ShadowController(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        private const val TAG = "ShadowController"
    }

    private fun dispatch(keyCode: Int) {
        try {
            val now = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        } catch (e: Exception) {
            Log.w(TAG, "dispatchMediaKeyEvent failed", e)
        }
    }

    fun pauseMedia() = dispatch(KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun resumeMedia() = dispatch(KeyEvent.KEYCODE_MEDIA_PLAY)
    fun toggleMedia() = dispatch(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
}
