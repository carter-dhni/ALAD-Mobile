package com.alad.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alad.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: UserPreferencesRepository) : ViewModel() {
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _volumeRatio = MutableStateFlow(1.0f)
    val volumeRatio: StateFlow<Float> = _volumeRatio.asStateFlow()

    // --- Pazhvak-parity options ---
    private val _subtitlesEnabled = MutableStateFlow(true)
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

    private val _showOriginal = MutableStateFlow(false)
    val showOriginal: StateFlow<Boolean> = _showOriginal.asStateFlow()

    private val _karaoke = MutableStateFlow(false)
    val karaoke: StateFlow<Boolean> = _karaoke.asStateFlow()

    private val _liteMode = MutableStateFlow(false)
    val liteMode: StateFlow<Boolean> = _liteMode.asStateFlow()

    private val _shadowMode = MutableStateFlow(false)
    val shadowMode: StateFlow<Boolean> = _shadowMode.asStateFlow()

    private val _shadowAutopause = MutableStateFlow(true)
    val shadowAutopause: StateFlow<Boolean> = _shadowAutopause.asStateFlow()

    private val _audioSource = MutableStateFlow(UserPreferencesRepository.AUDIO_SOURCE_INTERNAL)
    val audioSource: StateFlow<String> = _audioSource.asStateFlow()

    private val _skipSilence = MutableStateFlow(true)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _obsidianUri = MutableStateFlow("")
    val obsidianUri: StateFlow<String> = _obsidianUri.asStateFlow()

    private val _subtitleSize = MutableStateFlow(1.0f)
    val subtitleSize: StateFlow<Float> = _subtitleSize.asStateFlow()

    init {
        viewModelScope.launch {
            _apiKey.value = repository.apiKeyFlow.first()
            _volumeRatio.value = repository.volumeRatioFlow.first()
            _subtitlesEnabled.value = repository.subtitlesEnabledFlow.first()
            _showOriginal.value = repository.showOriginalFlow.first()
            _karaoke.value = repository.karaokeFlow.first()
            _liteMode.value = repository.liteModeFlow.first()
            _shadowMode.value = repository.shadowModeFlow.first()
            _shadowAutopause.value = repository.shadowAutopauseFlow.first()
            _audioSource.value = repository.audioSourceFlow.first()
            _skipSilence.value = repository.skipSilenceFlow.first()
            _obsidianUri.value = repository.obsidianUriFlow.first()
            _subtitleSize.value = repository.subtitleSizeFlow.first()
        }
    }

    fun updateApiKey(key: String) { _apiKey.value = key }
    fun updateVolumeRatio(ratio: Float) { _volumeRatio.value = ratio }
    fun updateSubtitlesEnabled(v: Boolean) { _subtitlesEnabled.value = v }
    fun updateShowOriginal(v: Boolean) { _showOriginal.value = v }
    fun updateKaraoke(v: Boolean) { _karaoke.value = v }
    fun updateLiteMode(v: Boolean) { _liteMode.value = v }
    fun updateShadowMode(v: Boolean) { _shadowMode.value = v }
    fun updateShadowAutopause(v: Boolean) { _shadowAutopause.value = v }
    fun updateAudioSource(v: String) { _audioSource.value = v }
    fun updateSkipSilence(v: Boolean) { _skipSilence.value = v }
    fun updateObsidianUri(v: String) { _obsidianUri.value = v }
    fun updateSubtitleSize(v: Float) { _subtitleSize.value = v }

    fun saveSettings() {
        viewModelScope.launch {
            repository.updateApiKey(_apiKey.value)
            repository.updateVolumeRatio(_volumeRatio.value)
            repository.updateSubtitlesEnabled(_subtitlesEnabled.value)
            repository.updateShowOriginal(_showOriginal.value)
            repository.updateKaraoke(_karaoke.value)
            repository.updateLiteMode(_liteMode.value)
            repository.updateShadowMode(_shadowMode.value)
            repository.updateShadowAutopause(_shadowAutopause.value)
            repository.updateAudioSource(_audioSource.value)
            repository.updateSkipSilence(_skipSilence.value)
            repository.updateObsidianUri(_obsidianUri.value)
            repository.updateSubtitleSize(_subtitleSize.value)
        }
    }
}

class SettingsViewModelFactory(private val repository: UserPreferencesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
