package com.alad.app.core.network

import android.util.Base64
import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

/** Which side of the conversation a transcript belongs to. */
enum class TranscriptKind { INPUT, OUTPUT }

/** Dubbing strategy: full voice dubbing, or text-only subtitles (low data). */
enum class StreamMode { DUB, LITE }

class ALADWebSocketManager(private val client: OkHttpClient) {
    private var webSocket: WebSocket? = null
    var onBinaryMessageReceived: ((ByteArray) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    /** Fired for incremental input/output transcripts (subtitles, shadowing). */
    var onTranscriptReceived: ((TranscriptKind, String) -> Unit)? = null
    /** Fired when the model signals the end of a spoken turn (sentence boundary). */
    var onTurnComplete: (() -> Unit)? = null

    companion object {
        private const val TAG = "ALADWebSocketManager"
        private const val GEMINI_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val LIVE_TRANSLATE_MODEL = "models/gemini-3.5-live-translate-preview"
    }

    private var isSetupComplete = false
    private var currentMode: StreamMode = StreamMode.DUB

    /**
     * @param enableTranscription request input/output audio transcriptions alongside
     *        dubbed audio (powers floating subtitles, karaoke and shadowing).
     */
    fun connect(
        apiKey: String,
        targetLang: String,
        mode: StreamMode = StreamMode.DUB,
        enableTranscription: Boolean = true
    ) {
        // Kept for source compatibility with the old (apiKey, sourceLang, targetLang) call shape.
        connect(apiKey, "", targetLang, mode, enableTranscription)
    }

    fun connect(
        apiKey: String,
        @Suppress("UNUSED_PARAMETER") sourceLang: String,
        targetLang: String,
        mode: StreamMode = StreamMode.DUB,
        enableTranscription: Boolean = true
    ) {
        currentMode = mode
        val finalUrl = "$GEMINI_WS_URL?key=$apiKey"
        val request = Request.Builder().url(finalUrl).build()
        isSetupComplete = false

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to Gemini Live API")
                onStatusChanged?.invoke("Connected")
                sendGeminiSetup(targetLang, mode, enableTranscription)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)

                    if (json.has("serverContent") || json.has("server_content")) {
                        val serverContent = json.optJSONObject("serverContent")
                            ?: json.optJSONObject("server_content")
                        if (serverContent != null) {
                            // 1) Audio chunks (dubbed voice)
                            val modelTurn = serverContent.optJSONObject("modelTurn")
                                ?: serverContent.optJSONObject("model_turn")
                            val parts = modelTurn?.optJSONArray("parts")
                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    val inlineData = part.optJSONObject("inlineData")
                                        ?: part.optJSONObject("inline_data")
                                    if (inlineData != null) {
                                        val base64Data = inlineData.getString("data")
                                        val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                        onBinaryMessageReceived?.invoke(audioBytes)
                                    }
                                    // 2) Text parts (lite / text-only mode)
                                    val textPart = part.optString("text", null)
                                    if (textPart != null) {
                                        onTranscriptReceived?.invoke(TranscriptKind.OUTPUT, textPart)
                                    }
                                }
                            }
                            // 3) Incremental transcriptions (when requested in setup)
                            val inputTr = serverContent.optJSONObject("inputTranscription")
                                ?: serverContent.optJSONObject("input_transcription")
                            inputTr?.optString("text", null)?.let {
                                onTranscriptReceived?.invoke(TranscriptKind.INPUT, it)
                            }
                            val outputTr = serverContent.optJSONObject("outputTranscription")
                                ?: serverContent.optJSONObject("output_transcription")
                            outputTr?.optString("text", null)?.let {
                                onTranscriptReceived?.invoke(TranscriptKind.OUTPUT, it)
                            }
                            // 4) Turn boundary -> sentence boundary for shadowing
                            val turnComplete = serverContent.optBoolean("turnComplete",
                                serverContent.optBoolean("turn_complete", false))
                            if (turnComplete) onTurnComplete?.invoke()
                        }
                    } else if (json.has("setupComplete") || json.has("setup_complete")) {
                        isSetupComplete = true
                        onStatusChanged?.invoke("Gemini Ready")
                        pendingAudio.forEach { sendAudioNow(it) }
                        pendingAudio.clear()
                    } else if (json.has("error")) {
                        val errMessage = json.getJSONObject("error").optString("message", "Unknown error")
                        Log.e(TAG, "API Error: $errMessage")
                        onStatusChanged?.invoke("Error: $errMessage")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                onMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isSetupComplete = false
                Log.d(TAG, "Closing: $code $reason")
                onStatusChanged?.invoke("Disconnected: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isSetupComplete = false
                Log.e(TAG, "WebSocket error", t)
                onStatusChanged?.invoke("Error: ${t.message}")
            }
        })
    }

    private fun sendGeminiSetup(targetLang: String, mode: StreamMode, enableTranscription: Boolean) {
        val targetLangCode = targetLang.split("-")[0]
        val setupPayload = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", LIVE_TRANSLATE_MODEL)
                put("generationConfig", JSONObject().apply {
                    if (mode == StreamMode.LITE) {
                        // Text-only translated output: ~10x less data, subtitles without voice.
                        put("responseModalities", JSONArray().put("TEXT"))
                    } else {
                        put("responseModalities", JSONArray().put("AUDIO"))
                    }
                    put("translationConfig", JSONObject().apply {
                        put("targetLanguageCode", targetLangCode)
                        put("echoTargetLanguage", true)
                    })
                })
                if (enableTranscription && mode == StreamMode.DUB) {
                    // Incremental speech transcripts alongside dubbed audio:
                    // powers floating subtitles, karaoke highlight and shadowing.
                    put("inputAudioTranscription", JSONObject())
                    put("outputAudioTranscription", JSONObject())
                }
                // Keep long sessions alive without manual reconnects.
                put("contextWindowCompression", JSONObject().apply {
                    put("slidingWindow", JSONObject())
                })
                put("sessionResumption", JSONObject().apply {
                    put("handle", JSONObject.NULL)
                })
            })
        }
        webSocket?.send(setupPayload.toString())
    }

    private val pendingAudio = mutableListOf<String>()

    fun sendAudioData(pcmData: ByteArray) {
        val base64Audio = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        if (!isSetupComplete) {
            pendingAudio.add(base64Audio)
            if (pendingAudio.size > 8) pendingAudio.removeAt(0)
            return
        }
        sendAudioNow(base64Audio)
    }

    private fun sendAudioNow(base64Audio: String) {
        val inputPayload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("audio", JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", base64Audio)
                })
            })
        }
        webSocket?.send(inputPayload.toString())
    }

    fun disconnect() {
        pendingAudio.clear()
        webSocket?.close(1000, "User requested stop")
        webSocket = null
        onStatusChanged?.invoke("Disconnected")
    }
}
