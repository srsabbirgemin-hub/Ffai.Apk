package com.example.data.remote

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class LiveCallState {
    IDLE,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    MUTED,
    ERROR
}

data class LiveTranscript(
    val sender: String, // "USER" or "AI"
    val text: String,
    val isComplete: Boolean = true
)

class GeminiLiveClient(
    private val scope: CoroutineScope,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep alive for WebSocket
        .build()
) {
    private var webSocket: WebSocket? = null
    private var isConnected = false

    private val _callState = MutableStateFlow(LiveCallState.IDLE)
    val callState: StateFlow<LiveCallState> = _callState.asStateFlow()

    private val _incomingPcmAudio = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingPcmAudio: SharedFlow<ByteArray> = _incomingPcmAudio.asSharedFlow()

    private val _transcripts = MutableSharedFlow<LiveTranscript>(extraBufferCapacity = 32)
    val transcripts: SharedFlow<LiveTranscript> = _transcripts.asSharedFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun connect(
        apiKey: String,
        voiceName: String = "Kore",
        assistantName: String = "Nova",
        language: String = "English"
    ) {
        if (apiKey.isBlank()) {
            _callState.value = LiveCallState.ERROR
            _errorMessage.value = "Gemini API key is missing. Add your key in Floating AI settings."
            return
        }

        _callState.value = LiveCallState.CONNECTING
        _errorMessage.value = null

        val langPrompt = if (language.equals("Bengali", ignoreCase = true)) {
            "Respond naturally and fluently in Bengali (বাংলা) or English as addressed."
        } else {
            "Respond concisely and warmly in English."
        }

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d("GeminiLive", "Connected to Gemini Live WebSocket")
                _callState.value = LiveCallState.LISTENING

                // Send setup message
                val setupJson = JSONObject().apply {
                    put("setup", JSONObject().apply {
                        put("model", "models/gemini-2.0-flash-exp")
                        put("generationConfig", JSONObject().apply {
                            put("responseModalities", JSONArray().apply {
                                put("AUDIO")
                            })
                            put("speechConfig", JSONObject().apply {
                                put("voiceConfig", JSONObject().apply {
                                    put("prebuiltVoiceConfig", JSONObject().apply {
                                        put("voiceName", voiceName)
                                    })
                                })
                            })
                        })
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "You are $assistantName, a helpful, quick and natural floating AI voice assistant. $langPrompt Keep spoken replies brief and conversational.")
                                })
                            })
                        })
                    })
                }

                webSocket.send(setupJson.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("GeminiLive", "WebSocket Failure: ${t.message}", t)
                isConnected = false
                _callState.value = LiveCallState.ERROR
                _errorMessage.value = "Voice connection error: ${t.localizedMessage ?: "Unknown"}"
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                isConnected = false
                _callState.value = LiveCallState.IDLE
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                _callState.value = LiveCallState.IDLE
            }
        })
    }

    private fun handleIncomingMessage(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val serverContent = root.optJSONObject("serverContent") ?: return

            val modelTurn = serverContent.optJSONObject("modelTurn")
            if (modelTurn != null) {
                _callState.value = LiveCallState.SPEAKING
                val parts = modelTurn.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val mime = inlineData.optString("mimeType", "")
                            if (mime.startsWith("audio/pcm")) {
                                val b64 = inlineData.optString("data", "")
                                if (b64.isNotEmpty()) {
                                    val pcmBytes = Base64.decode(b64, Base64.DEFAULT)
                                    _incomingPcmAudio.tryEmit(pcmBytes)
                                }
                            }
                        }
                        val text = part.optString("text", "")
                        if (text.isNotEmpty()) {
                            _transcripts.tryEmit(LiveTranscript("AI", text))
                        }
                    }
                }
            }

            val turnComplete = serverContent.optBoolean("turnComplete", false)
            val interrupted = serverContent.optBoolean("interrupted", false)
            if (turnComplete || interrupted) {
                _callState.value = LiveCallState.LISTENING
            }
        } catch (e: Exception) {
            Log.e("GeminiLive", "Error parsing server message", e)
        }
    }

    fun sendPcmChunk(pcmData: ByteArray) {
        if (!isConnected || webSocket == null) return
        val b64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        val chunkJson = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", b64)
                    })
                })
            })
        }
        webSocket?.send(chunkJson.toString())
    }

    fun setMuted(muted: Boolean) {
        if (muted) {
            _callState.value = LiveCallState.MUTED
        } else {
            _callState.value = LiveCallState.LISTENING
        }
    }

    fun disconnect() {
        isConnected = false
        try {
            webSocket?.close(1000, "User ended call")
        } catch (e: Exception) {
            // Ignore
        }
        webSocket = null
        _callState.value = LiveCallState.IDLE
    }
}
