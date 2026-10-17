package com.alad.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "alad_settings")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val WS_URL = stringPreferencesKey("ws_url")
        val API_KEY = stringPreferencesKey("api_key")
        val SOURCE_LANG = stringPreferencesKey("source_lang")
        val TARGET_LANG = stringPreferencesKey("target_lang")
        val VOLUME_RATIO = floatPreferencesKey("volume_ratio")

        // --- Pazhvak-parity features ---
        val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
        val SHOW_ORIGINAL = booleanPreferencesKey("show_original")
        val KARAOKE = booleanPreferencesKey("karaoke")
        val LITE_MODE = booleanPreferencesKey("lite_mode")
        val SHADOW_MODE = booleanPreferencesKey("shadow_mode")
        val SHADOW_AUTOPAUSE = booleanPreferencesKey("shadow_autopause")
        val AUDIO_SOURCE = stringPreferencesKey("audio_source") // "internal" | "mic"
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val OBSIDIAN_URI = stringPreferencesKey("obsidian_uri")
        val SUBTITLE_SIZE = floatPreferencesKey("subtitle_size")

        const val AUDIO_SOURCE_INTERNAL = "internal"
        const val AUDIO_SOURCE_MIC = "mic"
    }

    val wsUrlFlow: Flow<String> = context.dataStore.data.map { it[WS_URL] ?: "ws://192.168.1.100:8000/ws/dub" }
    val apiKeyFlow: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val sourceLangFlow: Flow<String> = context.dataStore.data.map { it[SOURCE_LANG] ?: "en" }
    val targetLangFlow: Flow<String> = context.dataStore.data.map { it[TARGET_LANG] ?: "fa" }
    val volumeRatioFlow: Flow<Float> = context.dataStore.data.map { it[VOLUME_RATIO] ?: 1.0f }

    val subtitlesEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[SUBTITLES_ENABLED] ?: true }
    val showOriginalFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_ORIGINAL] ?: false }
    val karaokeFlow: Flow<Boolean> = context.dataStore.data.map { it[KARAOKE] ?: false }
    val liteModeFlow: Flow<Boolean> = context.dataStore.data.map { it[LITE_MODE] ?: false }
    val shadowModeFlow: Flow<Boolean> = context.dataStore.data.map { it[SHADOW_MODE] ?: false }
    val shadowAutopauseFlow: Flow<Boolean> = context.dataStore.data.map { it[SHADOW_AUTOPAUSE] ?: true }
    val audioSourceFlow: Flow<String> = context.dataStore.data.map { it[AUDIO_SOURCE] ?: AUDIO_SOURCE_INTERNAL }
    val skipSilenceFlow: Flow<Boolean> = context.dataStore.data.map { it[SKIP_SILENCE] ?: true }
    val obsidianUriFlow: Flow<String> = context.dataStore.data.map { it[OBSIDIAN_URI] ?: "" }
    val subtitleSizeFlow: Flow<Float> = context.dataStore.data.map { it[SUBTITLE_SIZE] ?: 1.0f }

    suspend fun updateWsUrl(url: String) { context.dataStore.edit { it[WS_URL] = url } }
    suspend fun updateApiKey(key: String) { context.dataStore.edit { it[API_KEY] = key } }
    suspend fun updateSourceLang(lang: String) { context.dataStore.edit { it[SOURCE_LANG] = lang } }
    suspend fun updateTargetLang(lang: String) { context.dataStore.edit { it[TARGET_LANG] = lang } }
    suspend fun updateVolumeRatio(ratio: Float) { context.dataStore.edit { it[VOLUME_RATIO] = ratio } }

    suspend fun updateSubtitlesEnabled(v: Boolean) { context.dataStore.edit { it[SUBTITLES_ENABLED] = v } }
    suspend fun updateShowOriginal(v: Boolean) { context.dataStore.edit { it[SHOW_ORIGINAL] = v } }
    suspend fun updateKaraoke(v: Boolean) { context.dataStore.edit { it[KARAOKE] = v } }
    suspend fun updateLiteMode(v: Boolean) { context.dataStore.edit { it[LITE_MODE] = v } }
    suspend fun updateShadowMode(v: Boolean) { context.dataStore.edit { it[SHADOW_MODE] = v } }
    suspend fun updateShadowAutopause(v: Boolean) { context.dataStore.edit { it[SHADOW_AUTOPAUSE] = v } }
    suspend fun updateAudioSource(v: String) { context.dataStore.edit { it[AUDIO_SOURCE] = v } }
    suspend fun updateSkipSilence(v: Boolean) { context.dataStore.edit { it[SKIP_SILENCE] = v } }
    suspend fun updateObsidianUri(v: String) { context.dataStore.edit { it[OBSIDIAN_URI] = v } }
    suspend fun updateSubtitleSize(v: Float) { context.dataStore.edit { it[SUBTITLE_SIZE] = v } }
}
