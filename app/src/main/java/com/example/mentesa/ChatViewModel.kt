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
// --- IMPORTS DO ROOM ---
import com.example.mentesa.data.db.AppDatabase
import com.example.mentesa.data.db.ChatDao
import com.example.mentesa.data.db.ChatMessageEntity
import com.example.mentesa.data.db.ConversationInfo
import com.example.mentesa.data.db.ConversationMetadataDao // <<-- Import DAO metadados
import com.example.mentesa.data.db.ConversationMetadataEntity // <<-- Import Entidade metadados
// --- FIM IMPORTS ROOM ---
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.* // Necessário para flowOf, SharingStarted, combine, mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Imports para ChatMessage e Sender
import com.example.mentesa.ChatMessage
import com.example.mentesa.Sender

// --- NOVA DATA CLASS (pode ficar aqui ou em arquivo separado) ---
/**
 * Representa um item de conversa pronto para exibição no Drawer,
 * já contendo o título correto (customizado ou padrão).
 */
data class ConversationDisplayItem(
    val id: Long,
    val displayTitle: String,
    val lastTimestamp: Long
)
// --- FIM NOVA DATA CLASS ---


// Enum para estado de carregamento
enum class LoadingState { IDLE, LOADING, ERROR }

// --- CONSTANTES ---
const val NEW_CONVERSATION_ID = -1L
private const val MAX_HISTORY_MESSAGES = 20


@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    // Instâncias dos DAOs
    private val appDb = AppDatabase.getDatabase(application)
    private val chatDao: ChatDao = appDb.chatDao()
    private val metadataDao: ConversationMetadataDao = appDb.conversationMetadataDao() // <<-- OBTÉM NOVO DAO

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

    // --- Fluxos Internos ---
    // Flow original de ConversationInfo (baseado em chat_messages)
    private val rawConversationsFlow: Flow<List<ConversationInfo>> = chatDao.getConversations()
        .catch { e ->
            Log.e("ChatViewModel", "Error loading raw conversations flow", e);
            _errorMessage.value = "Erro ao carregar lista de conversas (raw)."
            emit(emptyList())
        }

    // Flow que observa os metadados (baseado em conversation_metadata)
    private val metadataFlow: Flow<List<ConversationMetadataEntity>> = metadataDao.getAllMetadata()
        .catch { e ->
            Log.e("ChatViewModel", "Error loading metadata flow", e);
            emit(emptyList())
        }

    // --- NOVO STATEFLOW COMBINADO PARA A UI DO DRAWER ---
    /**
     * Combina o Flow da lista de conversas (ID, Timestamp) com o Flow de metadados (títulos)
     * para produzir uma lista de [ConversationDisplayItem] pronta para a UI.
     * Reage a mudanças em ambas as tabelas. Usa SharingStarted.Eagerly para updates rápidos.
     */
    val conversationListForDrawer: StateFlow<List<ConversationDisplayItem>> =
        combine(rawConversationsFlow, metadataFlow) { conversations, metadataList ->
            Log.d("ChatViewModel", "Combining ${conversations.size} convs and ${metadataList.size} metadata entries.")
            val metadataMap = metadataList.associateBy({ it.conversationId }, { it.customTitle })

            // Mapeia cada ConversationInfo para ConversationDisplayItem
            // Usar map normal e chamar generateFallbackTitle (suspend) para cada item
            conversations.map { convInfo ->
                val customTitle = metadataMap[convInfo.id]?.takeIf { it.isNotBlank() }
                // Chama função suspend helper para obter título fallback (acessa DB)
                val finalTitle = customTitle ?: generateFallbackTitle(convInfo.id)

                ConversationDisplayItem(
                    id = convInfo.id,
                    displayTitle = finalTitle,
                    lastTimestamp = convInfo.lastTimestamp
                )
            }
        }
            .flowOn(Dispatchers.Default) // Combine/map pode usar Default dispatcher
            .catch { e ->
                Log.e("ChatViewModel", "Error combining conversations and metadata", e)
                // Define erro na thread principal se o combine falhar
                withContext(Dispatchers.Main.immediate) {
                    _errorMessage.value = "Erro ao processar lista de conversas para exibição."
                }
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly, // Garante reatividade na UI
                initialValue = emptyList() // Começa vazio
            )
    // --- FIM DO NOVO STATEFLOW ---


    // Definição do messages (com saudação inicial e estrutura do 'when' corrigida)
    val messages: StateFlow<List<ChatMessage>> = _currentConversationId.flatMapLatest { convId ->
        Log.d("ChatViewModel", "[State] CurrentConversationId changed: $convId")
        when (convId) {
            null, NEW_CONVERSATION_ID -> {
                flowOf(listOf(ChatMessage(welcomeMessageText, Sender.BOT)))
            }
            else ->
                chatDao.getMessagesForConversation(convId)
                    .map { entities ->
                        Log.d("ChatViewModel", "[State] Mapping ${entities.size} entities for conv $convId")
                        mapEntitiesToUiMessages(entities)
                    }
                    .catch { e ->
                        Log.e("ChatViewModel", "Error loading messages for conversation $convId", e)
                        // Define erro na thread principal
                        withContext(Dispatchers.Main.immediate) {
                            _errorMessage.value = "Erro ao carregar mensagens da conversa."
                        }
                        emit(emptyList())
                    }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    // Mensagem de boas vindas
    private val welcomeMessageText = "Olá! 😊 Eu sou o Mente Sã, seu assistente virtual de saúde mental, e é um prazer te conhecer. Como você está se sentindo hoje? Estou aqui para te acompanhar com empatia e respeito, oferecendo um espaço seguro e acolhedor para você se expressar. Existe algo em particular que gostaria de conversar ou explorar?"

    // Prompt Base
    private val menteSaSystemPrompt = """...""".trimIndent() // SEU PROMPT AQUI

    // Modelo Gemini
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash-latest",
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content { text(menteSaSystemPrompt) },
        requestOptions = RequestOptions(timeout = 60000)
    )

    init {
        loadInitialConversationOrStartNew()
    }

    // Lógica de inicialização (agora usa o flow combinado)
    private fun loadInitialConversationOrStartNew() {
        viewModelScope.launch {
            // Espera um pouco para o StateFlow combinado ter chance de emitir
            delay(150) // Aumentar ligeiramente o delay pode ajudar com Eagerly/combine
            val initialDisplayList = conversationListForDrawer.value
            Log.d("ChatViewModel", "[Init] Initial display list check (using .value): ${initialDisplayList.size}")
            val latestConversationId = initialDisplayList.firstOrNull()?.id
            if (_currentConversationId.value == null) {
                // Define como nulo se não houver conversas, ou o ID da última
                _currentConversationId.value = latestConversationId
                Log.i("ChatViewModel", "[Init] Setting initial conversation ID to: ${_currentConversationId.value}")
            } else {
                Log.d("ChatViewModel","[Init] Initial conversation ID already set to ${_currentConversationId.value}. Skipping.")
            }
        }
    }

    // --- Funções Chamadas pela UI ---

    // startNewConversation (Completa)
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

    // selectConversation (Completa)
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

    // sendMessage (Completa)
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

        // Cria nova conversa se necessário ANTES de salvar/chamar API
        if (isStartingNewConversation) {
            targetConversationId = timestamp
            Log.i("ChatViewModel", "Action: Creating new conversation with potential ID: $targetConversationId")
            // Define o ID atual ANTES de salvar a primeira msg, para que a UI reaja
            _currentConversationId.value = targetConversationId
            // Salva metadados iniciais (sem título customizado) para garantir que a linha exista
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    metadataDao.insertOrUpdateMetadata(ConversationMetadataEntity(targetConversationId, null))
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error saving initial metadata for new conv $targetConversationId", e)
                }
            }
        }

        // Validação final do ID
        if (targetConversationId == null || targetConversationId == NEW_CONVERSATION_ID) {
            Log.e("ChatViewModel", "sendMessage Error: Invalid targetConversationId ($targetConversationId) after checking for new conversation.")
            _errorMessage.value = "Erro interno: Não foi possível determinar a conversa."
            _loadingState.value = LoadingState.IDLE
            return
        }

        val userUiMessage = ChatMessage(userMessageText, Sender.USER)
        saveMessageToDb(userUiMessage, targetConversationId, timestamp)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentMessagesFromDb = chatDao.getMessagesForConversation(targetConversationId).first()
                val historyForApi = mapMessagesToApiHistory(mapEntitiesToUiMessages(currentMessagesFromDb))
                Log.d("ChatViewModel", "API Call: Sending ${historyForApi.size} history messages for conv $targetConversationId")
                callGeminiApi(userMessageText, historyForApi, targetConversationId)

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error preparing history or calling API for conv $targetConversationId", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao processar histórico ou chamar IA: ${e.message}"
                    _loadingState.value = LoadingState.ERROR
                }
            }
        }
    }

    // deleteConversation (Modificada para deletar metadados)
    fun deleteConversation(conversationId: Long) {
        if (conversationId == NEW_CONVERSATION_ID) {
            Log.w("ChatViewModel", "Attempted to delete invalid NEW_CONVERSATION_ID conversation.")
            return
        }
        Log.i("ChatViewModel", "Action: Deleting conversation $conversationId and its metadata")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Deleta as mensagens
                chatDao.clearConversation(conversationId)
                // 2. Deleta os metadados (título personalizado)
                metadataDao.deleteMetadata(conversationId) // <<-- DELETA METADADOS

                Log.i("ChatViewModel", "Conversation $conversationId and metadata deleted successfully from DB.")

                // Lógica para atualizar a conversa selecionada (se necessário)
                if (_currentConversationId.value == conversationId) {
                    // Busca as conversas restantes DIRETAMENTE do DAO após a exclusão
                    val remainingConversations = chatDao.getConversations().first()

                    withContext(Dispatchers.Main) {
                        val nextConversationId = remainingConversations.firstOrNull()?.id
                        if (nextConversationId != null) {
                            Log.i("ChatViewModel", "Deleted current conversation, selecting next available from DB: $nextConversationId")
                            _currentConversationId.value = nextConversationId
                        } else {
                            Log.i("ChatViewModel", "Deleted current conversation, no others left in DB. Starting new conversation flow.")
                            _currentConversationId.value = NEW_CONVERSATION_ID
                        }
                    }
                }
                // A UI que observa 'conversationListForDrawer' deve atualizar automaticamente.
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error deleting conversation $conversationId or its metadata", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao excluir conversa: ${e.localizedMessage}"
                }
            }
        }
    }

    // renameConversation (NOVA FUNÇÃO)
    fun renameConversation(conversationId: Long, newTitle: String) {
        if (conversationId == NEW_CONVERSATION_ID) {
            Log.w("ChatViewModel", "Cannot rename NEW_CONVERSATION_ID.")
            _errorMessage.value = "Não é possível renomear uma conversa não salva."
            return
        }
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isBlank()) {
            Log.w("ChatViewModel", "Cannot rename conversation $conversationId to blank title.")
            _errorMessage.value = "O título não pode ficar em branco."
            return
        }

        Log.i("ChatViewModel", "Action: Renaming conversation $conversationId to '$trimmedTitle'")
        val metadata = ConversationMetadataEntity(conversationId = conversationId, customTitle = trimmedTitle)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                metadataDao.insertOrUpdateMetadata(metadata) // <<-- SALVA NOVO TÍTULO
                Log.i("ChatViewModel", "Conversation $conversationId renamed successfully in DB.")
                // O Flow 'conversationListForDrawer' que usa 'combine' com 'metadataFlow'
                // DEVE ser notificado e re-emitir a lista atualizada, fazendo a UI reagir.
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error renaming conversation $conversationId", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao renomear conversa: ${e.localizedMessage}"
                }
            }
        }
    }


    // callGeminiApi (Completa)
    private suspend fun callGeminiApi(
        userMessageText: String,
        historyForApi: List<Content>,
        conversationId: Long
    ) {
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


    // saveMessageToDb (Completa)
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
    // mapEntitiesToUiMessages (Completa)
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

    // mapUiMessageToEntity (Completa)
    private fun mapUiMessageToEntity(message: ChatMessage, conversationId: Long, timestamp: Long): ChatMessageEntity {
        return ChatMessageEntity(
            conversationId = conversationId,
            text = message.text,
            sender = message.sender.name,
            timestamp = timestamp
        )
    }

    // mapMessagesToApiHistory (Completa, com return@map)
    private fun mapMessagesToApiHistory(messages: List<ChatMessage>): List<Content> { // <<-- CORREÇÃO 3 APLICADA
        return messages
            .takeLast(MAX_HISTORY_MESSAGES)
            .map { msg ->
                val role = if (msg.sender == Sender.USER) "user" else "model"
                return@map content(role = role) { text(msg.text) } // <<-- return@map INCLUÍDO
            }
    }


    // getDisplayTitle (Modificada para buscar título customizado e usar var)
    // Esta função ainda é usada para buscar o título inicial para o diálogo de renomear.
    suspend fun getDisplayTitle(conversationId: Long): String {
        return withContext(Dispatchers.IO) {
            var titleResult: String // Usando var
            if (conversationId == NEW_CONVERSATION_ID) {
                titleResult = "Nova Conversa" // Simplificado
            } else {
                try {
                    // 1. Tenta buscar título personalizado PRIMEIRO
                    val customTitle = metadataDao.getCustomTitle(conversationId) // <<-- USA NOVO DAO
                    if (!customTitle.isNullOrBlank()) {
                        Log.d("ChatViewModel", "Using custom title for $conversationId: '$customTitle'")
                        titleResult = customTitle // Usa o título personalizado
                    } else {
                        // 2. Se não há título personalizado, tenta a primeira mensagem do usuário
                        titleResult = generateFallbackTitle(conversationId) // Usa helper
                    }
                } catch (dbException: Exception) {
                    // Erro geral ao buscar título (customizado ou da mensagem)
                    Log.e("ChatViewModel", "Error fetching title data for conv $conversationId", dbException)
                    titleResult = "Conversa $conversationId" // Fallback
                }
            }
            titleResult // Retorna o título determinado
        }
    }

    // Função helper para gerar título fallback (usada no combine e getDisplayTitle)
    private suspend fun generateFallbackTitle(conversationId: Long): String = withContext(Dispatchers.IO) {
        // Não checa NEW_CONVERSATION_ID aqui, pois quem chama já deve ter feito
        try {
            val firstUserMessageText = chatDao.getFirstUserMessageText(conversationId)
            if (!firstUserMessageText.isNullOrBlank()) {
                Log.d("ChatViewModel", "Generating fallback title for $conversationId using first message.")
                return@withContext firstUserMessageText.take(30) + if (firstUserMessageText.length > 30) "..." else ""
            } else {
                // Tenta usar a data/hora
                try {
                    Log.d("ChatViewModel", "Generating fallback title for $conversationId using date.")
                    return@withContext "Conversa ${titleDateFormatter.format(Date(conversationId))}"
                } catch (formatException: Exception) {
                    Log.w("ChatViewModel", "Could not format conversationId $conversationId as Date for fallback title.", formatException)
                    return@withContext "Conversa $conversationId" // Fallback final
                }
            }
        } catch (dbException: Exception) {
            Log.e("ChatViewModel", "Error generating fallback title for conv $conversationId", dbException)
            return@withContext "Conversa $conversationId" // Fallback em caso de erro no DB
        }
    }


    // Companion Object
    companion object {
        private val titleDateFormatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
} // Fim da classe ChatViewModel - ~471 Linhas