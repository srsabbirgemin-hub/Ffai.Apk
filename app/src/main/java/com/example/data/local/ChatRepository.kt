package com.example.data.local

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {

    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForSession(sessionId)

    fun searchMessages(query: String): Flow<List<ChatMessageEntity>> =
        chatDao.searchMessages(query)

    suspend fun saveMessage(
        sessionId: String,
        sender: String,
        content: String,
        isVoice: Boolean = false,
        sessionTitle: String? = null
    ) {
        val type = if (isVoice) "VOICE_TRANSCRIPT" else "CHAT"
        val message = ChatMessageEntity(
            sessionId = sessionId,
            sender = sender,
            content = content,
            timestamp = System.currentTimeMillis(),
            type = type
        )
        chatDao.insertMessage(message)

        val snippet = if (content.length > 60) content.take(60) + "..." else content
        val title = sessionTitle ?: snippet
        val session = ChatSessionEntity(
            sessionId = sessionId,
            title = title,
            lastMessage = snippet,
            timestamp = System.currentTimeMillis(),
            isVoiceSession = isVoice
        )
        chatDao.insertSession(session)
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
        chatDao.deleteSessionMessages(sessionId)
    }

    suspend fun clearAll() {
        chatDao.clearAllSessions()
        chatDao.clearAllMessages()
    }
}
