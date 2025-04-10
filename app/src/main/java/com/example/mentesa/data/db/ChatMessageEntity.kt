package com.example.mentesa.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,

    @ColumnInfo(name = "message_text")
    val text: String,

    @ColumnInfo(name = "sender_type")
    val sender: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
