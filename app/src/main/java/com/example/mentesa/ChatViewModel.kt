package com.example.mentesa

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
// Removido: import kotlinx.coroutines.withContext (se não for mais usado)

// Imports para ChatMessage e Sender (ajuste o pacote se necessário)
import com.example.mentesa.ChatMessage
import com.example.mentesa.Sender


enum class LoadingState {
    IDLE, LOADING, ERROR
}

// ChatUiState não precisa mais de isPending
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val loadingState: LoadingState = LoadingState.IDLE,
    val errorMessage: String? = null
)

private const val MAX_HISTORY_MESSAGES = 20

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState: MutableStateFlow<ChatUiState> =
        MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> =
        _uiState.asStateFlow()

    // Removido: DAO do Room (voltamos ao estado sem persistência)

    private val menteSaSystemPrompt = """
    {... Cole o seu prompt base completo aqui ...}
    """.trimIndent()

    private val welcomeMessageText = "Olá! Eu sou o MenteSã, seu assistente virtual de saúde mental. Estou aqui para te acompanhar com empatia e respeito na sua jornada de bem-estar. Como você está se sentindo hoje?"

    // Inicialização do Modelo Generativo Gemini (com chave hardcoded temporária)
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash", // Ou "gemini-2.5-pro-exp-03-25"
        apiKey = "AIzaSyB6d6F-Dex-lS-B2CXySlYSQayiSSI9ms0", // <<< CHAVE HARDCODED TEMPORÁRIA
        systemInstruction = content { text(menteSaSystemPrompt) },
        requestOptions = RequestOptions(timeout = 60000)
    )

    init {
        // Adiciona mensagem de boas vindas inicial se estado estiver vazio
        if (_uiState.value.messages.isEmpty()) {
            _uiState.update {
                it.copy(messages = listOf(ChatMessage(welcomeMessageText, Sender.BOT)))
            }
        }
    }

    /**
     * Envia a mensagem do usuário para a API Gemini USANDO STREAMING e incluindo histórico,
     * atualizando a UI incrementalmente.
     * @param userMessage O texto digitado pelo usuário.
     */
    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || _uiState.value.loadingState == LoadingState.LOADING) {
            return
        }

        val currentMessagesForHistory = _uiState.value.messages // Histórico antes da nova msg
        val userUiMsg = ChatMessage(userMessage, Sender.USER)

        // Atualiza UI apenas com msg do usuário e estado LOADING
        // Não adicionamos mais a msg "pendente" aqui
        _uiState.update { currentState ->
            currentState.copy(
                messages = currentState.messages + userUiMsg,
                loadingState = LoadingState.LOADING,
                errorMessage = null
            )
        }

        // Prepara histórico para API (sem filtrar isPending, pois não existe mais)
        val historyForApi = mapMessagesToApiHistory(currentMessagesForHistory)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chat = generativeModel.startChat(history = historyForApi)

                // --- MUDANÇA PRINCIPAL: USA sendMessageStream ---
                val responseFlow: Flow<GenerateContentResponse> = chat.sendMessageStream(
                    content(role = "user") { text(userMessage) }
                )

                var botMessageIndex = -1 // Índice da msg do Bot na lista da UI
                var currentBotText = ""  // Texto acumulado

                responseFlow
                    .catch { e -> // Tratamento de erro no Flow
                        Log.e("ChatViewModel", "Streaming Error", e)
                        _uiState.update { currentState ->
                            // Remove a msg parcial do bot se existir e houve erro
                            val messagesWithError = if (botMessageIndex != -1 && botMessageIndex < currentState.messages.size) {
                                currentState.messages.subList(0, botMessageIndex)
                            } else {
                                currentState.messages
                            }
                            currentState.copy(
                                loadingState = LoadingState.ERROR,
                                errorMessage = e.localizedMessage ?: "Erro durante a resposta da IA.",
                                messages = messagesWithError
                            )
                        }
                    }
                    .onCompletion { cause -> // Ao completar (com ou sem erro)
                        // Seta IDLE APENAS se o estado ainda for LOADING
                        // (para não sobrescrever o estado ERROR definido no catch)
                        if (_uiState.value.loadingState == LoadingState.LOADING) {
                            _uiState.update { it.copy(loadingState = LoadingState.IDLE) }
                        }
                        Log.d("ChatViewModel", "Streaming completed. Cause: $cause")
                    }
                    .collect { chunk -> // Processa cada chunk
                        chunk.text?.let { textPart ->
                            currentBotText += textPart // Acumula texto

                            if (botMessageIndex == -1) {
                                // PRIMEIRO CHUNK: Adiciona a nova mensagem do BOT (ainda incompleta)
                                val newBotMsg = ChatMessage(currentBotText, Sender.BOT)
                                _uiState.update { currentState ->
                                    val newMessages = currentState.messages + newBotMsg
                                    botMessageIndex = newMessages.lastIndex // Guarda o índice
                                    currentState.copy(messages = newMessages)
                                }
                            } else {
                                // CHUNKS SEGUINTES: Atualiza a mensagem existente do BOT
                                val updatedBotMsg = ChatMessage(currentBotText, Sender.BOT)
                                _uiState.update { currentState ->
                                    val updatedMessages = currentState.messages.toMutableList()
                                    if (botMessageIndex < updatedMessages.size) {
                                        updatedMessages[botMessageIndex] = updatedBotMsg
                                    }
                                    currentState.copy(messages = updatedMessages)
                                }
                            }
                        }
                    }
                // --- FIM DA COLETA DO STREAM ---

            } catch (e: Exception) { // Erro geral (ex: startChat falha)
                Log.e("ChatViewModel", "Error sending message context", e)
                _uiState.update { currentState ->
                    currentState.copy(
                        loadingState = LoadingState.ERROR,
                        errorMessage = e.localizedMessage ?: "Erro na comunicação com a IA."
                    )
                }
            } finally {
                // Garante que IDLE seja setado se algo der muito errado fora do flow
                if (_uiState.value.loadingState != LoadingState.IDLE && _uiState.value.loadingState != LoadingState.ERROR) {
                    _uiState.update { it.copy(loadingState = LoadingState.IDLE) }
                }
            }
        }
    }
}

// Removido: saveMessageToDb, mappers de/para Entity

/** Mapeia histórico da UI (memória) para o formato da API Gemini */
private fun mapMessagesToApiHistory(messages: List<ChatMessage>): List<Content> {
    return messages
        // Removido: .filterNot { it.isPending }
        .takeLast(MAX_HISTORY_MESSAGES)
        .map { msg ->
            content(role = if (msg.sender == Sender.USER) "user" else "model") {
                text(msg.text)
            }
        }
}