package com.example.mentesa.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidade para armazenar metadados adicionais da conversa, como um título personalizado.
 */
@Entity(tableName = "conversation_metadata")
data class ConversationMetadataEntity(

    // Chave primária é o próprio ID da conversa, que corresponde ao
    // conversationId usado em ChatMessageEntity.
    @PrimaryKey
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,

    // Título personalizado definido pelo usuário (pode ser nulo se nenhum foi definido)
    @ColumnInfo(name = "custom_title")
    val customTitle: String?
)