package com.alad.app.core.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.alad.app.core.obsidian.ObsidianExporter
import com.alad.app.core.shadow.SentenceReplayPlayer
import com.alad.app.core.shadow.ShadowController
import com.alad.app.data.repository.UserPreferencesRepository
import com.alad.app.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Floating subtitle bar shown over any app while dubbing.
 * Shows the live translated transcript (and optionally the original),
 * a karaoke word-by-word highlight, and the shadowing practice card.
 */
class SubtitleOverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private var params: WindowManager.LayoutParams? = null

    private val savedStateRegistryOwner by lazy { ServiceSavedStateRegistryOwner(this) }
    private val viewModelStoreOwner by lazy { ServiceViewModelStoreOwner() }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 140
        }

        val repository = UserPreferencesRepository(applicationContext)
        val shadowController = ShadowController(applicationContext)
        val replayPlayer = SentenceReplayPlayer()

        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ALADTheme {
                    SubtitleOverlayContent(
                        repository = repository,
                        onDrag = { dx, dy ->
                            params?.x = (params?.x ?: 0) + dx.toInt()
                            params?.y = (params?.y ?: 0) - dy.toInt()
                            windowManager.updateViewLayout(composeView, params)
                        },
                        onReplay = { slow ->
                            val card = LiveSessionState.shadowCard.value
                            val pcm = LiveSessionState.sentenceBuffer.snapshot(card?.replaySeconds ?: 20)
                            if (pcm != null) replayPlayer.play(pcm, slow)
                        },
                        onSaveToObsidian = {
                            val card = LiveSessionState.shadowCard.value
                            if (card != null) {
                                lifecycleScope.launch {
                                    val uri = repository.obsidianUriFlow.first()
                                    val lang = repository.targetLangFlow.first()
                                    val res = ObsidianExporter.saveSentence(
                                        applicationContext, uri, card.original, card.translated, lang
                                    )
                                    Toast.makeText(applicationContext, res.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onResume = {
                            replayPlayer.stop()
                            LiveSessionState.shadowCard.value = null
                            LiveSessionState.shadowPaused.value = false
                            shadowController.resumeMedia()
                        }
                    )
                }
            }
        }

        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)

        windowManager.addView(composeView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
    }
}

@Composable
private fun SubtitleOverlayContent(
    repository: UserPreferencesRepository,
    onDrag: (Float, Float) -> Unit,
    onReplay: (Boolean) -> Unit,
    onSaveToObsidian: () -> Unit,
    onResume: () -> Unit
) {
    val translated by LiveSessionState.subtitleTranslated.collectAsState()
    val original by LiveSessionState.subtitleOriginal.collectAsState()
    val shadowCard by LiveSessionState.shadowCard.collectAsState()
    val karaokeWords by LiveSessionState.karaokeWords.collectAsState()
    val karaokeSpoken by LiveSessionState.karaokeSpoken.collectAsState()
    val scope = rememberCoroutineScope()

    var showOriginal by remember { mutableStateOf(false) }
    var karaokeOn by remember { mutableStateOf(false) }
    var textScale by remember { mutableStateOf(1.0f) }

    LaunchedEffect(Unit) {
        launch { repository.showOriginalFlow.collect { showOriginal = it } }
        launch { repository.karaokeFlow.collect { karaokeOn = it } }
        launch { repository.subtitleSizeFlow.collect { textScale = it } }
    }

    // Hide entirely when there is nothing to show and no shadow card.
    if (translated.isBlank() && original.isBlank() && shadowCard == null) {
        Box(modifier = Modifier.size(1.dp))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (shadowCard != null) {
            ShadowCardView(
                card = shadowCard!!,
                textScale = textScale,
                onReplay = onReplay,
                onSave = onSaveToObsidian,
                onResume = onResume
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (translated.isNotBlank() || original.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showOriginal && original.isNotBlank()) {
                        Text(
                            text = original,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = (13 * textScale).sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (karaokeOn && karaokeWords.isNotEmpty()) {
                        Text(
                            text = buildAnnotatedString {
                                karaokeWords.forEachIndexed { i, w ->
                                    if (i < karaokeSpoken) {
                                        withStyle(SpanStyle(color = NeonCyan, fontWeight = FontWeight.Bold)) {
                                            append(w)
                                        }
                                    } else {
                                        withStyle(SpanStyle(color = Color.White)) { append(w) }
                                    }
                                    append(" ")
                                }
                            },
                            fontSize = (17 * textScale).sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = (24 * textScale).sp
                        )
                    } else if (translated.isNotBlank()) {
                        Text(
                            text = translated,
                            color = Color.White,
                            fontSize = (17 * textScale).sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = (24 * textScale).sp
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ShadowCardView(
    card: ShadowCard,
    textScale: Float,
    onReplay: (Boolean) -> Unit,
    onSave: () -> Unit,
    onResume: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101A30).copy(alpha = 0.94f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Shadowing · listen and repeat",
            color = NeonCyan,
            fontSize = (12 * textScale).sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = card.original.ifBlank { card.translated },
            color = Color.White,
            fontSize = (20 * textScale).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = (28 * textScale).sp
        )
        if (card.original.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = card.translated,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = (15 * textScale).sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onReplay(false) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Replay", tint = Color.White)
            }
            IconButton(onClick = { onReplay(true) }) {
                Icon(Icons.Default.SlowMotionVideo, contentDescription = "Replay slow", tint = NeonCyan)
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Star, contentDescription = "Save to Obsidian", tint = NeonCoral)
            }
            IconButton(onClick = onResume) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume video", tint = NeonCyan)
            }
        }
    }
}
