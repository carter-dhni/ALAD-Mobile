package com.alad.app.core.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.alad.app.TransparentCaptureActivity
import com.alad.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: toggle live dubbing without leaving the current app.
 * Turning on routes through [TransparentCaptureActivity] (or straight to the
 * service in mic/around-me mode); turning off stops the dubbing service.
 */
@RequiresApi(Build.VERSION_CODES.N)
class DubTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (AudioDubbingForegroundService.isRunning.value) {
            val intent = Intent(this, AudioDubbingForegroundService::class.java).apply {
                action = AudioDubbingForegroundService.ACTION_STOP
            }
            startService(intent)
        } else {
            scope.launch {
                val repository = UserPreferencesRepository(applicationContext)
                val useMic = repository.audioSourceFlow.first() ==
                    UserPreferencesRepository.AUDIO_SOURCE_MIC
                if (useMic) {
                    // No screen-capture permission needed for microphone mode.
                    val intent = Intent(applicationContext, AudioDubbingForegroundService::class.java).apply {
                        action = AudioDubbingForegroundService.ACTION_START
                        putExtra(
                            AudioDubbingForegroundService.EXTRA_AUDIO_SOURCE,
                            UserPreferencesRepository.AUDIO_SOURCE_MIC
                        )
                    }
                    startForegroundServiceCompat(intent)
                } else {
                    val intent = Intent(applicationContext, TransparentCaptureActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    // TileService is not an Activity context; collapse shade first.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startActivityAndCollapse(intent)
                    } else {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(intent)
                    }
                }
            }
        }
        updateTile()
    }

    private fun updateTile() {
        qsTile?.let { tile ->
            val running = AudioDubbingForegroundService.isRunning.value
            tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    private fun startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
    }
}
