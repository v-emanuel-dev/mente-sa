package com.example.mentesa.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query("""
        SELECT conversation_id as id, MAX(timestamp) as lastTimestamp
        FROM chat_messages
        GROUP BY conversation_id
        ORDER BY lastTimestamp DESC
    """)
    fun getConversations(): Flow<List<ConversationInfo>>

    @Query("""
        SELECT conversation_id as id, MAX(timestamp) as lastTimestamp
        FROM chat_messages
        WHERE user_id = :userId
        GROUP BY conversation_id
        ORDER BY lastTimestamp DESC
    """)
    fun getConversationsForUser(userId: String): Flow<List<ConversationInfo>>

    @Query("""
        SELECT message_text FROM chat_messages
        WHERE conversation_id = :conversationId AND sender_type = 'USER'
        ORDER BY timestamp ASC
        LIMIT 1
    """)
    suspend fun getFirstUserMessageText(conversationId: Long): String?

    @Query("DELETE FROM chat_messages WHERE conversation_id = :conversationId")
    suspend fun clearConversation(conversationId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}