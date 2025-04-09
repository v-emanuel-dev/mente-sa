package com.example.mentesa

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel // Precisa ser AndroidViewModel para o Contexto
import androidx.lifecycle.viewModelScope // Import necessário
import com.example.mentesa.BuildConfig // Import do BuildConfig (manual)
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
// --- IMPORTS DO ROOM ---
// Certifique-se que o pacote está correto!
import com.example.mentesa.data.db.AppDatabase
import com.example.mentesa.data.db.ChatDao
import com.example.mentesa.data.db.ChatMessageEntity
// --- FIM IMPORTS ROOM ---
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Imports para ChatMessage e Sender (devem existir no seu projeto)
import com.example.mentesa.ChatMessage
import com.example.mentesa.Sender


enum class LoadingState {
    IDLE, LOADING, ERROR
}

// ChatUiState não usa mais isPending
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val loadingState: LoadingState = LoadingState.IDLE,
    val errorMessage: String? = null
)

private const val MAX_HISTORY_MESSAGES = 20 // Limite para API

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState: MutableStateFlow<ChatUiState> =
        MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> =
        _uiState.asStateFlow()

    // --- Obtém instância do DAO ---
    private val chatDao: ChatDao = AppDatabase.getDatabase(application).chatDao()

    // Prompt Base MenteSã
    private val menteSaSystemPrompt = """
    {... Cole o seu prompt base completo aqui ...}
    """.trimIndent()

    // Mensagem de boas-vindas
    private val welcomeMessageText = "Olá! Eu sou o MenteSã, seu assistente virtual de saúde mental. Estou aqui para te acompanhar com empatia e respeito na sua jornada de bem-estar. Como você está se sentindo hoje?"

    // Modelo Gemini com chave do BuildConfig
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash", // Ou o modelo que decidiu usar
        apiKey = BuildConfig.GEMINI_API_KEY, // Acessando via BuildConfig (manual)
        systemInstruction = content { text(menteSaSystemPrompt) },
        requestOptions = RequestOptions(timeout = 60000)
    )

    init {
        // Carrega histórico do banco de dados ao iniciar
        loadChatHistory()
    }

    // Carrega histórico do BD usando Flow
    private fun loadChatHistory() {
        viewModelScope.launch {
            chatDao.getAllMessages()
                .map { entities -> mapEntitiesToUiMessages(entities) }
                .distinctUntilChanged()
                .catch { e ->
                    Log.e("ChatViewModel", "Error loading chat history", e)
                    _uiState.update { it.copy(errorMessage = "Erro ao carregar histórico: ${e.message}") }
                }
                .collect { messagesFromDb ->
                    // Verifica se precisa adicionar/salvar msg de boas vindas
                    // (faz isso apenas se o BD estiver vazio na primeira coleta)
                    if (messagesFromDb.isEmpty() && _uiState.value.messages.isEmpty()) {
                        val welcomeUiMsg = ChatMessage(welcomeMessageText, Sender.BOT)
                        saveMessageToDb(welcomeUiMsg) // Salva no BD
                        // O Flow será emitido novamente pelo Room com a mensagem salva
                        Log.d("ChatViewModel", "Database was empty, saved welcome message.")
                    } else {
                        // Atualiza o estado da UI com as mensagens do BD
                        _uiState.update { it.copy(messages = messagesFromDb) }
                        if (messagesFromDb.isNotEmpty()) { // Evita log para o caso inicial vazio antes da msg de boas vindas ser inserida
                            Log.d("ChatViewModel", "Loaded/Updated ${messagesFromDb.size} messages from DB.")
                        }
                    }
                }
        }
    }

    // Envia mensagem, atualiza UI, salva no BD, chama API com histórico
    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || _uiState.value.loadingState == LoadingState.LOADING) { return }

        val currentMessagesForHistory = _uiState.value.messages // Pega msgs atuais para histórico da API
        val userUiMsg = ChatMessage(userMessage, Sender.USER)

        // 1. Atualiza UI com mensagem do usuário e Loading
        _uiState.update { currentState ->
            currentState.copy(
                messages = currentState.messages + userUiMsg,
                loadingState = LoadingState.LOADING,
                errorMessage = null
            )
        }
        // 2. Salva mensagem do usuário no BD
        saveMessageToDb(userUiMsg)

        // 3. Prepara histórico para API (com base nas mensagens ANTES da atual do user)
        val historyForApi = mapMessagesToApiHistory(currentMessagesForHistory)

        // 4. Chama API em background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chat = generativeModel.startChat(history = historyForApi)
                val responseFlow: Flow<GenerateContentResponse> = chat.sendMessageStream(content(role = "user") { text(userMessage) })

                var botMessageIndex = -1
                var currentBotText = ""
                var finalBotUiMsg: ChatMessage? = null

                responseFlow
                    .catch { e -> /* ... Lógica de erro do stream ... */
                        Log.e("ChatViewModel", "Streaming Error", e)
                        _uiState.update { currentState ->
                            // Reverte UI para estado ANTES da msg do usuário ser adicionada
                            currentState.copy(
                                loadingState = LoadingState.ERROR,
                                errorMessage = e.localizedMessage ?: "Erro durante a resposta da IA.",
                                messages = currentMessagesForHistory // Reverte a lista
                            )
                        }
                    }
                    .onCompletion { cause ->
                        if (cause == null) {
                            // Sucesso: Salva a mensagem COMPLETA do bot no BD
                            finalBotUiMsg?.let { saveMessageToDb(it) }
                        }
                        // Sempre volta para IDLE após stream (catch trata o ERROR)
                        _uiState.update { it.copy(loadingState = LoadingState.IDLE) }
                        Log.d("ChatViewModel", "Streaming completed. Cause: $cause")
                    }
                    .collect { chunk ->
                        chunk.text?.let { textPart ->
                            currentBotText += textPart
                            val botUiMsgInProgress = ChatMessage(currentBotText, Sender.BOT)
                            finalBotUiMsg = botUiMsgInProgress // Guarda a última versão

                            if (botMessageIndex == -1) { // Primeiro chunk
                                _uiState.update { currentState ->
                                    // Adiciona a nova msg do bot (incompleta)
                                    // Note: currentState.messages aqui JÁ contém a msg do user adicionada antes do launch
                                    val newMessages = currentState.messages + botUiMsgInProgress
                                    botMessageIndex = newMessages.lastIndex
                                    currentState.copy(messages = newMessages)
                                }
                            } else { // Chunks seguintes
                                _uiState.update { currentState ->
                                    // Atualiza a msg do bot existente na lista
                                    val updatedMessages = currentState.messages.toMutableList()
                                    if (botMessageIndex < updatedMessages.size) {
                                        updatedMessages[botMessageIndex] = botUiMsgInProgress
                                    }
                                    currentState.copy(messages = updatedMessages)
                                }
                            }
                        }
                    }
            } catch (e: Exception) { // Erro antes do stream (ex: startChat)
                Log.e("ChatViewModel", "Error sending message context", e)
                _uiState.update { currentState ->
                    // Reverte UI para estado ANTES da msg do usuário
                    currentState.copy(
                        loadingState = LoadingState.ERROR,
                        errorMessage = e.localizedMessage ?: "Erro na comunicação com a IA.",
                        messages = currentMessagesForHistory
                    )
                }
            }
        }
    }

    // Função para salvar no BD
    private fun saveMessageToDb(message: ChatMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = mapUiMessageToEntity(message)
            try {
                chatDao.insertMessage(entity) // Usa chatDao
                Log.d("ChatViewModel", "Message inserted to DB: ${entity.text.take(30)}...")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error inserting message into DB", e)
            }
        }
    }

    // Funções de Mapeamento (UI <-> Entidade BD)
    private fun mapEntitiesToUiMessages(entities: List<ChatMessageEntity>): List<ChatMessage> {
        return entities.map { entity ->
            ChatMessage(
                text = entity.text,
                sender = try { enumValueOf<Sender>(entity.sender.uppercase()) }
                catch (e: IllegalArgumentException) { Sender.BOT } // Default seguro
            )
        }
    }

    private fun mapUiMessageToEntity(message: ChatMessage): ChatMessageEntity {
        return ChatMessageEntity(
            text = message.text,
            sender = message.sender.name, // Converte Enum para String
            timestamp = System.currentTimeMillis() // Gera timestamp ao salvar
        )
    }

    // Mapper para Histórico da API (usando ChatMessage da UI/memória)
    private fun mapMessagesToApiHistory(messages: List<ChatMessage>): List<Content> {
        return messages
            .takeLast(MAX_HISTORY_MESSAGES)
            .map { msg -> content(role = if (msg.sender == Sender.USER) "user" else "model") { text(msg.text) } }
    }
}