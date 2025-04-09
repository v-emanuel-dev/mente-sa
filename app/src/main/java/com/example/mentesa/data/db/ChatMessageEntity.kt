package com.example.mentesa.data.db // Pacote onde o arquivo está

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa a tabela "chat_messages" no banco de dados Room.
 * Cada instância desta classe é uma linha na tabela, ou seja, uma mensagem salva.
 */
@Entity(tableName = "chat_messages") // Define o nome da tabela no banco de dados
data class ChatMessageEntity(

    @PrimaryKey(autoGenerate = true) // Define 'id' como chave primária autoincrementável
    val id: Int = 0,

    // Armazena o texto da mensagem.
    @ColumnInfo(name = "message_text")
    val text: String,

    // Armazena quem enviou. Usamos String ("USER" ou "BOT").
    @ColumnInfo(name = "sender_type")
    val sender: String,

    // Armazena o timestamp (momento) em que a mensagem foi criada/recebida. Útil para ordenar.
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)