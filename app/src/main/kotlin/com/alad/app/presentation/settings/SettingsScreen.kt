package com.alad.app.presentation.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alad.app.R
import com.alad.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val apiKey by viewModel.apiKey.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var apiKeyVisible by remember { mutableStateOf(false) }

    AmbientBackground {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        tint = Color.White
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Main API Key Configuration Glass Card
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    borderColor = NeonCyan,
                    borderAlpha = 0.25f
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = stringResource(R.string.gemini_api_key),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Google AI Studio Live API",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = viewModel::updateApiKey,
                            placeholder = { Text("AIzaSy...", color = TextSecondary.copy(alpha = 0.6f)) },
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0x25FFFFFF),
                                unfocusedContainerColor = Color(0x12FFFFFF),
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (apiKeyVisible) stringResource(R.string.hide) else stringResource(R.string.show),
                                        tint = if (apiKeyVisible) NeonCyan else TextSecondary
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.api_key_guide),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Get API Key Quick Action Pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            NeonCyan.copy(alpha = 0.15f),
                                            NeonPurple.copy(alpha = 0.15f)
                                        )
                                    )
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        Brush.horizontalGradient(
                                            listOf(
                                                NeonCyan.copy(alpha = 0.4f),
                                                NeonPurple.copy(alpha = 0.4f)
                                            )
                                        )
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    uriHandler.openUri("https://aistudio.google.com/app/apikey")
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.get_api_key),
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // ---- Translation options (Pazhvak parity) ----
                TranslationOptionsCard(viewModel)

                // ---- Audio source options ----
                AudioOptionsCard(viewModel)

                // ---- Shadowing (language practice) ----
                ShadowingOptionsCard(viewModel)

                Spacer(modifier = Modifier.height(4.dp))

                // Save Settings Button
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isPressed) 0.98f else 1.0f,
                    label = "save_btn_scale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(NeonCyan, NeonBlue, NeonViolet)
                            )
                        )
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                viewModel.saveSettings()
                                val savedMessage = context.getString(R.string.settings_saved)
                                scope.launch {
                                    snackbarHostState.showSnackbar(savedMessage)
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.save_settings),
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NeonPurple.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NeonCyan,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
private fun TranslationOptionsCard(viewModel: SettingsViewModel) {
    val subtitles by viewModel.subtitlesEnabled.collectAsState()
    val showOriginal by viewModel.showOriginal.collectAsState()
    val karaoke by viewModel.karaoke.collectAsState()
    val liteMode by viewModel.liteMode.collectAsState()
    val subtitleSize by viewModel.subtitleSize.collectAsState()

    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeader(Icons.Default.ClosedCaption, stringResource(R.string.settings_translation))
            Spacer(modifier = Modifier.height(8.dp))
            ToggleRow(
                stringResource(R.string.opt_subtitles),
                stringResource(R.string.opt_subtitles_desc),
                subtitles, viewModel::updateSubtitlesEnabled
            )
            ToggleRow(
                stringResource(R.string.opt_show_original),
                stringResource(R.string.opt_show_original_desc),
                showOriginal, viewModel::updateShowOriginal
            )
            ToggleRow(
                stringResource(R.string.opt_karaoke),
                stringResource(R.string.opt_karaoke_desc),
                karaoke, viewModel::updateKaraoke
            )
            ToggleRow(
                stringResource(R.string.opt_lite_mode),
                stringResource(R.string.opt_lite_mode_desc),
                liteMode, viewModel::updateLiteMode
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.opt_subtitle_size),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
            Slider(
                value = subtitleSize,
                onValueChange = viewModel::updateSubtitleSize,
                valueRange = 0.7f..1.6f,
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
        }
    }
}

@Composable
private fun AudioOptionsCard(viewModel: SettingsViewModel) {
    val audioSource by viewModel.audioSource.collectAsState()
    val skipSilence by viewModel.skipSilence.collectAsState()
    val useMic = audioSource == com.alad.app.data.repository.UserPreferencesRepository.AUDIO_SOURCE_MIC

    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeader(Icons.Default.Mic, stringResource(R.string.settings_audio))
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val internalSelected = !useMic
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (internalSelected) NeonCyan.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .border(
                            1.dp,
                            if (internalSelected) NeonCyan else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            viewModel.updateAudioSource(
                                com.alad.app.data.repository.UserPreferencesRepository.AUDIO_SOURCE_INTERNAL
                            )
                        }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.opt_audio_internal),
                            color = if (internalSelected) NeonCyan else Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        Text(
                            stringResource(R.string.opt_audio_internal_desc),
                            color = TextSecondary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (useMic) NeonCyan.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .border(
                            1.dp,
                            if (useMic) NeonCyan else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            viewModel.updateAudioSource(
                                com.alad.app.data.repository.UserPreferencesRepository.AUDIO_SOURCE_MIC
                            )
                        }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.opt_audio_mic),
                            color = if (useMic) NeonCyan else Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        Text(
                            stringResource(R.string.opt_audio_mic_desc),
                            color = TextSecondary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            ToggleRow(
                stringResource(R.string.opt_skip_silence),
                stringResource(R.string.opt_skip_silence_desc),
                skipSilence, viewModel::updateSkipSilence
            )
        }
    }
}

@Composable
private fun ShadowingOptionsCard(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val shadowMode by viewModel.shadowMode.collectAsState()
    val autopause by viewModel.shadowAutopause.collectAsState()
    val obsidianUri by viewModel.obsidianUri.collectAsState()

    val vaultPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.updateObsidianUri(uri.toString())
            } catch (_: Exception) { }
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionHeader(Icons.Default.RecordVoiceOver, stringResource(R.string.settings_shadowing))
            Spacer(modifier = Modifier.height(8.dp))
            ToggleRow(
                stringResource(R.string.opt_shadow_mode),
                stringResource(R.string.opt_shadow_mode_desc),
                shadowMode, viewModel::updateShadowMode
            )
            ToggleRow(
                stringResource(R.string.opt_shadow_autopause),
                stringResource(R.string.opt_shadow_autopause_desc),
                autopause, viewModel::updateShadowAutopause
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.opt_obsidian),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
            Text(
                stringResource(R.string.opt_obsidian_desc),
                color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (obsidianUri.isNotBlank()) NeonCyan.copy(alpha = 0.12f)
                        else Color.White.copy(alpha = 0.05f)
                    )
                    .border(
                        1.dp,
                        if (obsidianUri.isNotBlank()) NeonCyan else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { vaultPicker.launch(null) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FolderOpen, contentDescription = null,
                        tint = if (obsidianUri.isNotBlank()) NeonCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (obsidianUri.isNotBlank()) stringResource(R.string.opt_obsidian_connected)
                        else stringResource(R.string.opt_obsidian_pick),
                        color = if (obsidianUri.isNotBlank()) NeonCyan else Color.White,
                        fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
