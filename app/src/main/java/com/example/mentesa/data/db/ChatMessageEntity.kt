package com.example.mentesa.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa a tabela "chat_messages" no banco de dados Room.
 * Cada instância desta classe é uma linha na tabela, ou seja, uma mensagem salva.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Adicione este campo para identificar a qual conversa a mensagem pertence
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,

    @ColumnInfo(name = "message_text")
    val text: String,

    @ColumnInfo(name = "sender_type")
    val sender: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)