package com.example.mentesa

/**
 * Representa os possíveis remetentes de uma mensagem no chat.
 */
enum class Sender {
    USER, // Mensagem enviada pelo usuário
    BOT   // Mensagem recebida do chatbot (MenteSã)
    // Poderíamos adicionar outros tipos, como SYSTEM (para erros ou avisos) se necessário.
}

/**
 * Data class para representar uma única mensagem na interface de chat.
 *
 * @property text O conteúdo textual da mensagem.
 * @property sender Quem enviou a mensagem (USER ou BOT).
 * @property isPending Opcional: Poderia ser usado para indicar se a resposta do BOT ainda está sendo gerada.
 */
data class ChatMessage(
    val text: String,
    val sender: Sender,
    val isPending: Boolean = false // Usaremos isso para a mensagem do BOT enquanto carrega
)