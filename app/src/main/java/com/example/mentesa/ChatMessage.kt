package com.example.mentesa // Ou seu pacote correto

enum class Sender {
    USER, BOT
}

/**
 * Data class para representar uma única mensagem na interface de chat.
 * (Propriedade isPending removida)
 */
data class ChatMessage(
    val text: String,
    val sender: Sender
)