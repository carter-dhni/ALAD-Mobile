package com.alad.app.core.service

import com.alad.app.core.shadow.SentenceBuffer
import kotlinx.coroutines.flow.MutableStateFlow

/** One finalized sentence pair for the shadowing overlay card. */
data class ShadowCard(
    val original: String,
    val translated: String,
    /** seconds of input audio kept for replay */
    val replaySeconds: Int = 20
)

/**
 * Process-wide live state shared between the dubbing foreground service and
 * the subtitle overlay service. All flows are updated on the service's IO
 * scope; collectors must be prepared for updates from any thread.
 */
object LiveSessionState {
    /** Latest incremental input-language transcript (what was said). */
    val subtitleOriginal = MutableStateFlow("")

    /** Latest incremental translated transcript (what it means). */
    val subtitleTranslated = MutableStateFlow("")

    /** Current shadowing card, or null when shadowing is off / no sentence yet. */
    val shadowCard = MutableStateFlow<ShadowCard?>(null)

    /** Words of the current translated sentence for karaoke highlight. */
    val karaokeWords = MutableStateFlow<List<String>>(emptyList())

    /** How many of [karaokeWords] have been "spoken" by the dubbed voice. */
    val karaokeSpoken = MutableStateFlow(0)

    /** True while the shadowing card is waiting for the learner to repeat. */
    val shadowPaused = MutableStateFlow(false)

    /** Recent input audio for sentence replay in the shadowing card. */
    val sentenceBuffer = SentenceBuffer()

    fun reset() {
        subtitleOriginal.value = ""
        subtitleTranslated.value = ""
        shadowCard.value = null
        karaokeWords.value = emptyList()
        karaokeSpoken.value = 0
        shadowPaused.value = false
    }
}
