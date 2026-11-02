package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.audio.VoiceCallManager
import com.example.data.local.AppDatabase
import com.example.data.local.ChatRepository
import com.example.data.pref.EncryptedPreferencesManager
import com.example.data.remote.GroqClient

class FloatingAIApp : Application() {

    companion object {
        const val OVERLAY_CHANNEL_ID = "floating_ai_overlay_channel"
        lateinit var instance: FloatingAIApp
            private set
    }

    lateinit var database: AppDatabase
        private set
    lateinit var repository: ChatRepository
        private set
    lateinit var preferencesManager: EncryptedPreferencesManager
        private set
    lateinit var groqClient: GroqClient
        private set
    lateinit var voiceCallManager: VoiceCallManager
        private set
    lateinit var ttsManager: com.example.data.audio.TTSManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        repository = ChatRepository(database.chatDao())
        preferencesManager = EncryptedPreferencesManager(this)
        groqClient = GroqClient()
        voiceCallManager = VoiceCallManager(this, preferencesManager, repository)
        ttsManager = com.example.data.audio.TTSManager(this)

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                OVERLAY_CHANNEL_ID,
                "Floating AI Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for the Floating AI orb, voice call, and chat panel"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
