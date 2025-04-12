package com.example.mentesa.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_metadata")
data class ConversationMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,

    @ColumnInfo(name = "custom_title")
    val customTitle: String?,

    @ColumnInfo(name = "user_id")
    val userId: String = "local_user"
)