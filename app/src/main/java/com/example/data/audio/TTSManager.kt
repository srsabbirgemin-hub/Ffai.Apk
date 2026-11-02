package com.example.data.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TTSManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentlySpokenId = MutableStateFlow<Long?>(null)
    val currentlySpokenId: StateFlow<Long?> = _currentlySpokenId.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to instantiate TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentlySpokenId.value = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentlySpokenId.value = null
                }
            })
        } else {
            Log.w("TTSManager", "TextToSpeech init returned code $status")
        }
    }

    fun speak(text: String, messageId: Long? = null, language: String = "English") {
        if (!isInitialized || tts == null) return

        stop()

        try {
            val locale = if (language.equals("Bengali", ignoreCase = true)) {
                Locale("bn", "BD")
            } else {
                Locale.US
            }
            tts?.language = locale

            val utteranceId = messageId?.toString() ?: "msg_${System.currentTimeMillis()}"
            _currentlySpokenId.value = messageId
            _isSpeaking.value = true

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e("TTSManager", "Error speaking text", e)
            _isSpeaking.value = false
            _currentlySpokenId.value = null
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // Ignore
        }
        _isSpeaking.value = false
        _currentlySpokenId.value = null
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // Ignore
        }
        tts = null
    }
}
