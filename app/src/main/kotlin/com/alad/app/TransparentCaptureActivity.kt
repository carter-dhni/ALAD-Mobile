package com.alad.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alad.app.core.service.AudioDubbingForegroundService
import com.alad.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TransparentCaptureActivity : ComponentActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startDubbingService(result.resultCode, result.data, false)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
        finish() // Always finish the transparent activity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Around-me (mic) mode needs no screen-capture permission at all.
        lifecycleScope.launch {
            val repository = UserPreferencesRepository(applicationContext)
            val useMic = repository.audioSourceFlow.first() ==
                UserPreferencesRepository.AUDIO_SOURCE_MIC
            if (useMic) {
                startDubbingService(0, null, true)
                finish()
            } else {
                val mediaProjectionManager =
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
            }
        }
    }

    private fun startDubbingService(resultCode: Int, data: Intent?, useMic: Boolean) {
        val intent = Intent(this, AudioDubbingForegroundService::class.java).apply {
            action = AudioDubbingForegroundService.ACTION_START
            putExtra(AudioDubbingForegroundService.EXTRA_RESULT_CODE, resultCode)
            putExtra(
                AudioDubbingForegroundService.EXTRA_AUDIO_SOURCE,
                if (useMic) UserPreferencesRepository.AUDIO_SOURCE_MIC
                else UserPreferencesRepository.AUDIO_SOURCE_INTERNAL
            )
            if (data != null) {
                putExtra(AudioDubbingForegroundService.EXTRA_RESULT_DATA, data)
            }
        }
        ContextCompat.startForegroundService(this, intent)
    }
}
