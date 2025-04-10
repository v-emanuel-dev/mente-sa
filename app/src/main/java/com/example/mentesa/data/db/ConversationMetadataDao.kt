package com.example.mentesa.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationMetadataDao {

    @Upsert
    suspend fun insertOrUpdateMetadata(metadata: ConversationMetadataEntity)

    @Query("SELECT custom_title FROM conversation_metadata WHERE conversation_id = :conversationId")
    suspend fun getCustomTitle(conversationId: Long): String?

    @Query("DELETE FROM conversation_metadata WHERE conversation_id = :conversationId")
    suspend fun deleteMetadata(conversationId: Long)

    @Query("SELECT * FROM conversation_metadata")
    fun getAllMetadata(): Flow<List<ConversationMetadataEntity>>
}
