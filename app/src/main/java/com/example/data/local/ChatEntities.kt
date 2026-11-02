package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val sender: String, // "USER" or "AI"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "CHAT" // "CHAT" or "VOICE_TRANSCRIPT"
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messageCount: Int = 1,
    val isVoiceSession: Boolean = false
)
