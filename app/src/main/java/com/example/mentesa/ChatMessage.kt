package com.example.mentesa

enum class Sender {
    USER, BOT
}

data class ChatMessage(
    val text: String,
    val sender: Sender
)