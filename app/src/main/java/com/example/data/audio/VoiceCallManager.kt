package com.example.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.example.data.local.ChatRepository
import com.example.data.pref.EncryptedPreferencesManager
import com.example.data.remote.GeminiLiveClient
import com.example.data.remote.LiveCallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.sqrt

class VoiceCallManager(
    private val context: Context,
    private val prefs: EncryptedPreferencesManager,
    private val repository: ChatRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val geminiLiveClient = GeminiLiveClient(scope)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordJob: Job? = null
    private var playbackJob: Job? = null

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    private var currentSessionId: String = ""

    init {
        // Collect incoming audio chunks and feed AudioTrack
        scope.launch {
            geminiLiveClient.incomingPcmAudio.collect { pcmBytes ->
                playAudioChunk(pcmBytes)
            }
        }

        // Collect transcripts and save to Room DB
        scope.launch {
            geminiLiveClient.transcripts.collect { transcript ->
                _currentTranscript.value = "${transcript.sender}: ${transcript.text}"
                if (currentSessionId.isNotEmpty()) {
                    repository.saveMessage(
                        sessionId = currentSessionId,
                        sender = transcript.sender,
                        content = transcript.text,
                        isVoice = true,
                        sessionTitle = "Voice Call with ${prefs.getAssistantName()}"
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startCall() {
        if (_isCallActive.value) return
        _isCallActive.value = true
        _isMuted.value = false
        currentSessionId = "voice_${UUID.randomUUID().toString().take(8)}"
        _currentTranscript.value = "Connecting to ${prefs.getAssistantName()}..."

        val apiKey = prefs.getGeminiApiKey()
        val voice = prefs.getSelectedVoice()
        val name = prefs.getAssistantName()
        val lang = prefs.getLanguage()

        geminiLiveClient.connect(apiKey, voice, name, lang)

        initAudioTrack()
        startAudioRecording()
    }

    private fun initAudioTrack() {
        try {
            val sampleRate = 24000
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("VoiceCallManager", "Failed to init AudioTrack", e)
        }
    }

    private fun playAudioChunk(pcmBytes: ByteArray) {
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.write(pcmBytes, 0, pcmBytes.size)
                    computeAmplitude(pcmBytes)
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceCallManager", "Error playing audio chunk", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        recordJob?.cancel()
        recordJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("VoiceCallManager", "AudioRecord not initialized")
                    return@launch
                }

                audioRecord?.startRecording()
                val buffer = ByteArray(bufferSize)

                while (isActive && _isCallActive.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        if (!_isMuted.value) {
                            val chunk = buffer.copyOf(read)
                            geminiLiveClient.sendPcmChunk(chunk)
                            computeAmplitude(chunk)
                        } else {
                            _audioAmplitude.value = 0f
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceCallManager", "Recording error", e)
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    // Ignore
                }
                audioRecord = null
            }
        }
    }

    private fun computeAmplitude(pcmBytes: ByteArray) {
        if (pcmBytes.size < 2) return
        var sum = 0.0
        val sampleCount = pcmBytes.size / 2
        for (i in 0 until sampleCount) {
            val sample = (pcmBytes[i * 2 + 1].toInt() shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)
            sum += sample * sample
        }
        val rms = sqrt(sum / sampleCount)
        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        _audioAmplitude.value = normalized
    }

    fun toggleMute() {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        geminiLiveClient.setMuted(newMuted)
    }

    fun endCall() {
        _isCallActive.value = false
        recordJob?.cancel()
        playbackJob?.cancel()
        geminiLiveClient.disconnect()

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioTrack = null

        _audioAmplitude.value = 0f
        _currentTranscript.value = "Call ended"
    }

    fun previewVoice(voiceName: String) {
        scope.launch(Dispatchers.Default) {
            val sampleRate = 24000
            val durationSec = 1.2
            val numSamples = (sampleRate * durationSec).toInt()
            val buffer = ByteArray(numSamples * 2)

            // Voice frequency modulation based on voice name hash for distinctive pleasant preview tones
            val baseFreq = 300.0 + (Math.abs(voiceName.hashCode()) % 250)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val freq = baseFreq + 40.0 * Math.sin(2.0 * Math.PI * 5.0 * t)
                val envelope = Math.sin(Math.PI * (i.toDouble() / numSamples)) // smooth fade-in fade-out
                val sample = (Math.sin(2.0 * Math.PI * freq * t) * envelope * 24000).toInt().toShort()

                buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }

            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
            } catch (e: Exception) {
                Log.e("VoiceCallManager", "Failed preview tone", e)
            }
        }
    }

    fun destroy() {
        endCall()
        scope.cancel()
    }
}
