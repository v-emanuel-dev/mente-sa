package com.example.mentesa

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope // Import necessário
import com.example.mentesa.BuildConfig // Import do BuildConfig (manual)
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
// --- IMPORTS DO ROOM (VERIFIQUE O PACOTE 'data.db') ---
import com.example.mentesa.data.db.AppDatabase
import com.example.mentesa.data.db.ChatDao
import com.example.mentesa.data.db.ChatMessageEntity
import com.example.mentesa.data.db.ConversationInfo
// --- FIM IMPORTS ROOM ---
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay // Import necessário
import kotlinx.coroutines.flow.* // Necessário para flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Import necessário
// import kotlinx.coroutines.withTimeoutOrNull // Não mais usado na lógica atual
import java.text.SimpleDateFormat // Import necessário
import java.util.Date // Import necessário
import java.util.Locale // Import necessário

// Imports para ChatMessage e Sender (Verifique o pacote)
import com.example.mentesa.ChatMessage
import com.example.mentesa.Sender


// Enum para estado de carregamento
enum class LoadingState { IDLE, LOADING, ERROR }

// --- CONSTANTES ---
// Tornada pública anteriormente
const val NEW_CONVERSATION_ID = -1L
private const val MAX_HISTORY_MESSAGES = 20


@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    // Instância do DAO
    private val chatDao: ChatDao = AppDatabase.getDatabase(application).chatDao()

    // --- StateFlows Expostos para a UI ---
    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    private val _loadingState = MutableStateFlow(LoadingState.IDLE)
    val isLoading: StateFlow<Boolean> = _loadingState.map { it == LoadingState.LOADING }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Busca lista de conversas do DAO
    val conversations: StateFlow<List<ConversationInfo>> = chatDao.getConversations()
        .catch { e ->
            Log.e("ChatViewModel", "Error loading conversations", e);
            _errorMessage.value = "Erro ao carregar lista de conversas."
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // --- DEFINIÇÃO DO messages ATUALIZADA COM MENSAGEM DE BOAS-VINDAS ---
    val messages: StateFlow<List<ChatMessage>> = _currentConversationId.flatMapLatest { convId ->
        Log.d("ChatViewModel", "[State] CurrentConversationId changed: $convId")
        when (convId) {
            null, NEW_CONVERSATION_ID -> {
                Log.d("ChatViewModel", "[State] New conversation or null ID: Emitting welcome message.")
                // Emite um Flow contendo apenas a mensagem de boas-vindas
                flowOf(listOf(ChatMessage(welcomeMessageText, Sender.BOT))) // <-- Alteração aplicada aqui
            }
            else -> {
                // Lógica existente para carregar mensagens do banco para conversas existentes
                Log.d("ChatViewModel", "[State] Loading messages for conversation $convId")
                chatDao.getMessagesForConversation(convId)
                    .map { entities ->
                        Log.d("ChatViewModel", "[State] Mapping ${entities.size} entities for conv $convId")
                        mapEntitiesToUiMessages(entities)
                    }
                    .catch { e ->
                        Log.e("ChatViewModel", "Error loading messages for conversation $convId", e)
                        _errorMessage.value = "Erro ao carregar mensagens da conversa."
                        emit(emptyList())
                    }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList() // Valor inicial ainda é vazio
    )
    // --- FIM DA DEFINIÇÃO ATUALIZADA ---

    // Mensagem de boas vindas
    private val welcomeMessageText = "Olá! 👋 Eu sou o Mente Sã, seu assistente virtual de saúde mental. Estou aqui para te acompanhar com empatia e respeito na sua jornada de bem-estar. Como você está se sentindo hoje?"

    // Prompt Base (mantenha o seu prompt completo)
    private val menteSaSystemPrompt = """
    Você é o MenteSã, um chatbot de inteligência artificial especializado em oferecer suporte emocional e ferramentas psicoeducativas para pessoas que enfrentam transtornos mentais, como transtorno bipolar, ansiedade e depressão.

    🎯 Missão Principal
    Criar um ambiente seguro, empático e sem julgamentos, onde os usuários possam:

    Expressar sentimentos e pensamentos com liberdade.
    Obter informações confiáveis sobre saúde mental.
    Aprender estratégias práticas baseadas em Terapia Cognitivo-Comportamental (TCC).
    Ser encorajados a buscar ajuda profissional quando necessário.

    {... RESTANTE DO SEU PROMPT BASE COMPLETO ...}

    🗣️ Tom de Voz
    Calmo, gentil, acolhedor e respeitoso.
    Esperançoso, mas sempre realista.
    Livre de imposições, orientado por perguntas abertas e apoio gradual.
    """.trimIndent()

    // Modelo Gemini
    private val generativeModel = GenerativeModel(
        // Usar um modelo estável recomendado
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content { text(menteSaSystemPrompt) },
        requestOptions = RequestOptions(timeout = 60000) // Timeout 60s
    )

    init {
        // Carrega a conversa mais recente ao iniciar o ViewModel
        loadInitialConversationOrStartNew()
    }

    // Lógica de inicialização simplificada (corrigida anteriormente)
    private fun loadInitialConversationOrStartNew() {
        viewModelScope.launch {
            delay(100) // Pequeno delay para dar chance ao Flow inicializar
            val currentConversations = conversations.value
            Log.d("ChatViewModel", "[Init] Initial conversations check (using .value): ${currentConversations.size}")
            val latestConversationId = currentConversations.firstOrNull()?.id
            if (_currentConversationId.value == null) {
                _currentConversationId.value = latestConversationId
                Log.i("ChatViewModel", "[Init] Setting initial conversation ID to: ${_currentConversationId.value}")
            } else {
                Log.d("ChatViewModel","[Init] Initial conversation ID already set to ${_currentConversationId.value}. Skipping.")
            }
        }
    }

    // --- Funções Chamadas pela UI (startNewConversation, selectConversation, sendMessage) ---
    fun startNewConversation() {
        if (_currentConversationId.value != NEW_CONVERSATION_ID) {
            Log.i("ChatViewModel", "Action: Starting new conversation flow")
            _currentConversationId.value = NEW_CONVERSATION_ID
            _errorMessage.value = null
            _loadingState.value = LoadingState.IDLE
        } else {
            Log.d("ChatViewModel", "Action: Already in new conversation flow, ignoring startNewConversation.")
        }
    }

    fun selectConversation(conversationId: Long) {
        if (conversationId != _currentConversationId.value && conversationId != NEW_CONVERSATION_ID) {
            Log.i("ChatViewModel", "Action: Selecting conversation $conversationId")
            _currentConversationId.value = conversationId
            _errorMessage.value = null
            _loadingState.value = LoadingState.IDLE
        } else if (conversationId == _currentConversationId.value) {
            Log.d("ChatViewModel", "Action: Conversation $conversationId already selected, ignoring selectConversation.")
        } else {
            Log.w("ChatViewModel", "Action: Attempted to select invalid NEW_CONVERSATION_ID ($conversationId), ignoring.")
        }
    }

    fun sendMessage(userMessageText: String) {
        if (userMessageText.isBlank()) {
            Log.w("ChatViewModel", "sendMessage cancelled: Empty message.")
            return
        }
        if (_loadingState.value == LoadingState.LOADING) {
            Log.w("ChatViewModel", "sendMessage cancelled: Already loading.")
            _errorMessage.value = "Aguarde a resposta anterior."
            return
        }

        _loadingState.value = LoadingState.LOADING
        _errorMessage.value = null

        val timestamp = System.currentTimeMillis()
        var targetConversationId = _currentConversationId.value
        val isStartingNewConversation = (targetConversationId == null || targetConversationId == NEW_CONVERSATION_ID)

        if (isStartingNewConversation) {
            targetConversationId = timestamp
            Log.i("ChatViewModel", "Action: Creating new conversation with potential ID: $targetConversationId")
            _currentConversationId.value = targetConversationId
        }

        if (targetConversationId == null || targetConversationId == NEW_CONVERSATION_ID) {
            Log.e("ChatViewModel", "sendMessage Error: Invalid targetConversationId ($targetConversationId) after checking for new conversation.")
            _errorMessage.value = "Erro interno: Não foi possível determinar a conversa."
            _loadingState.value = LoadingState.IDLE
            return
        }

        val userUiMessage = ChatMessage(userMessageText, Sender.USER)
        // Salva a mensagem do usuário ANTES de chamar a API
        saveMessageToDb(userUiMessage, targetConversationId, timestamp)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Busca histórico atualizado (incluindo a msg do user)
                val currentMessagesFromDb = chatDao.getMessagesForConversation(targetConversationId).first()
                val historyForApi = mapMessagesToApiHistory(mapEntitiesToUiMessages(currentMessagesFromDb))

                Log.d("ChatViewModel", "API Call: Sending ${historyForApi.size} history messages for conv $targetConversationId")
                callGeminiApi(userMessageText, historyForApi, targetConversationId)

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error preparing history or calling API for conv $targetConversationId", e)
                withContext(Dispatchers.Main) {
                    // Mostra o erro do Room que vimos antes ou qualquer outro erro que ocorra aqui
                    _errorMessage.value = "Erro ao processar histórico ou chamar IA: ${e.message}"
                    _loadingState.value = LoadingState.ERROR
                }
            }
        }
    }

    // --- ADIÇÃO DA FUNÇÃO deleteConversation ---
    /**
     * Exclui todas as mensagens de uma conversa específica.
     * Se a conversa excluída for a atualmente selecionada, seleciona a próxima mais
     * recente ou inicia o fluxo de nova conversa.
     */
    fun deleteConversation(conversationId: Long) {
        // Evita tentar deletar a "nova conversa" que ainda não existe no DB
        if (conversationId == NEW_CONVERSATION_ID) {
            Log.w("ChatViewModel", "Attempted to delete invalid NEW_CONVERSATION_ID conversation.")
            return
        }
        Log.i("ChatViewModel", "Action: Deleting conversation $conversationId")
        viewModelScope.launch(Dispatchers.IO) { // Executa em background
            try {
                // Usa o método existente no DAO para limpar as mensagens
                chatDao.clearConversation(conversationId)
                Log.i("ChatViewModel", "Conversation $conversationId deleted successfully from DB.")

                // Verifica se a conversa excluída era a que estava ativa na UI
                if (_currentConversationId.value == conversationId) {
                    // Se sim, precisamos mudar a seleção para outra conversa ou "nova"
                    withContext(Dispatchers.Main) { // Volta para a thread principal para atualizar o ID
                        // O Flow 'conversations' DEVE atualizar automaticamente após a exclusão no BD.
                        // Pegamos o valor mais recente dele para decidir o que selecionar.
                        val remainingConversations = chatDao.getConversations().first()
                        val nextConversationId = remainingConversations.firstOrNull()?.id

                        if (nextConversationId != null) {
                            Log.i("ChatViewModel", "Deleted current conversation, selecting next available: $nextConversationId")
                            _currentConversationId.value = nextConversationId // Seleciona a próxima
                        } else {
                            // Se não sobrou nenhuma, vai para o estado de "nova conversa"
                            Log.i("ChatViewModel", "Deleted current conversation, no others left. Starting new conversation flow.")
                            _currentConversationId.value = NEW_CONVERSATION_ID // Inicia fluxo de nova conversa
                        }
                    }
                }
                // Se deletou uma conversa que não era a atual, não precisamos mudar a seleção.
                // A lista no drawer (que observa 'conversations') vai atualizar sozinha.

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error deleting conversation $conversationId", e)
                withContext(Dispatchers.Main) { // Mostra erro na UI
                    _errorMessage.value = "Erro ao excluir conversa: ${e.localizedMessage}"
                }
            }
        }
    }
    // --- FIM DA ADIÇÃO ---


    // --- Função callGeminiApi ---
    private suspend fun callGeminiApi(
        userMessageText: String,
        historyForApi: List<Content>,
        conversationId: Long
    ) {
        withContext(Dispatchers.IO) {
            var finalBotResponseText: String? = null
            try {
                Log.d("ChatViewModel", "Starting Gemini API call for conv $conversationId")
                val chat = generativeModel.startChat(history = historyForApi)
                val responseFlow: Flow<GenerateContentResponse> = chat.sendMessageStream(
                    content(role = "user") { text(userMessageText) }
                )
                var currentBotText = ""

                responseFlow
                    .mapNotNull { it.text }
                    .onEach { textPart ->
                        currentBotText += textPart
                        Log.v("ChatViewModel", "Stream chunk for conv $conversationId: '$textPart'")
                    }
                    .onCompletion { cause ->
                        if (cause == null) {
                            Log.i("ChatViewModel", "Stream completed successfully for conv $conversationId.")
                            finalBotResponseText = currentBotText
                        } else {
                            Log.e("ChatViewModel", "Stream completed with error for conv $conversationId", cause)
                            withContext(Dispatchers.Main) {
                                _errorMessage.value = "Erro durante a resposta da IA: ${cause.localizedMessage}"
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (!finalBotResponseText.isNullOrBlank()) {
                                saveMessageToDb(ChatMessage(finalBotResponseText!!, Sender.BOT), conversationId)
                            } else if (cause == null) {
                                Log.w("ChatViewModel", "Stream for conv $conversationId completed successfully but resulted in null/blank text.")
                            }
                            _loadingState.value = LoadingState.IDLE
                            Log.d("ChatViewModel", "Stream processing finished for conv $conversationId. Resetting loading state.")
                        }
                    }
                    .catch { e ->
                        Log.e("ChatViewModel", "Error during Gemini stream collection for conv $conversationId", e)
                        throw e
                    }
                    .collect()

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error setting up or starting Gemini API call for conv $conversationId", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao iniciar comunicação com IA: ${e.localizedMessage}"
                    _loadingState.value = LoadingState.ERROR
                }
            }
        }
    }


    // --- Função saveMessageToDb ---
    private fun saveMessageToDb(message: ChatMessage, conversationId: Long, timestamp: Long = System.currentTimeMillis()) {
        if (conversationId == NEW_CONVERSATION_ID) {
            Log.e("ChatViewModel", "Attempted to save message with invalid NEW_CONVERSATION_ID. Message: '${message.text.take(30)}...'")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val entity = mapUiMessageToEntity(message, conversationId, timestamp)
            try {
                chatDao.insertMessage(entity)
                Log.d("ChatViewModel", "Msg saved (Conv $conversationId, Sender ${entity.sender}): ${entity.text.take(50)}...")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error inserting message into DB for conv $conversationId", e)
            }
        }
    }


    // --- Funções de Mapeamento ---
    private fun mapEntitiesToUiMessages(entities: List<ChatMessageEntity>): List<ChatMessage> {
        return entities.mapNotNull { entity ->
            try {
                val sender = enumValueOf<Sender>(entity.sender.uppercase())
                ChatMessage(entity.text, sender)
            } catch (e: IllegalArgumentException) {
                Log.e("ChatViewModelMapper", "Invalid sender string in DB: ${entity.sender}. Skipping message ID ${entity.id}.")
                null
            }
        }
    }

    private fun mapUiMessageToEntity(message: ChatMessage, conversationId: Long, timestamp: Long): ChatMessageEntity {
        return ChatMessageEntity(
            conversationId = conversationId,
            text = message.text,
            sender = message.sender.name,
            timestamp = timestamp
        )
    }

    private fun mapMessagesToApiHistory(messages: List<ChatMessage>): List<Content> {
        return messages
            .takeLast(MAX_HISTORY_MESSAGES)
            .map { msg ->
                val role = if (msg.sender == Sender.USER) "user" else "model"
                content(role = role) { text(msg.text) }
            }
    }

    // --- Função getDisplayTitle ---
    suspend fun getDisplayTitle(conversationId: Long): String = withContext(Dispatchers.IO) {
        if (conversationId == NEW_CONVERSATION_ID) return@withContext "Nova Conversa (não salva)"

        try {
            val firstUserMessageText = chatDao.getFirstUserMessageText(conversationId)
            return@withContext if (!firstUserMessageText.isNullOrBlank()) {
                firstUserMessageText.take(30) + if (firstUserMessageText.length > 30) "..." else ""
            } else {
                try {
                    "Conversa ${titleDateFormatter.format(Date(conversationId))}"
                } catch (formatException: Exception) {
                    Log.w("ChatViewModel", "Could not format conversationId $conversationId as Date for title.", formatException)
                    "Conversa $conversationId"
                }
            }
        } catch (dbException: Exception) {
            Log.e("ChatViewModel", "Error fetching first user message text for conv $conversationId", dbException)
            "Conversa $conversationId"
        }
    }

    // Companion Object para o formatador de data
    companion object {
        private val titleDateFormatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
} // Fim da classe ChatViewModel