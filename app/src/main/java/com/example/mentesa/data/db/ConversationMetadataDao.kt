package com.example.mentesa.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMetadata(metadata: ConversationMetadataEntity)

    @Query("SELECT * FROM conversation_metadata")
    fun getAllMetadata(): Flow<List<ConversationMetadataEntity>>

    @Query("SELECT * FROM conversation_metadata WHERE user_id = :userId")
    fun getMetadataForUser(userId: String): Flow<List<ConversationMetadataEntity>>

    @Query("SELECT custom_title FROM conversation_metadata WHERE conversation_id = :conversationId")
    suspend fun getCustomTitle(conversationId: Long): String?

    @Query("DELETE FROM conversation_metadata WHERE conversation_id = :conversationId")
    suspend fun deleteMetadata(conversationId: Long)
}