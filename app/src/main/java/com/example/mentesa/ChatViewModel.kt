package com.example.mentesa

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.example.mentesa.data.db.AppDatabase
import com.example.mentesa.data.db.ChatDao
import com.example.mentesa.data.db.ChatMessageEntity
import com.example.mentesa.data.db.ConversationInfo
import com.example.mentesa.data.db.ConversationMetadataDao
import com.example.mentesa.data.db.ConversationMetadataEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LoadingState { IDLE, LOADING, ERROR }

const val NEW_CONVERSATION_ID = -1L
private const val MAX_HISTORY_MESSAGES = 20

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val appDb = AppDatabase.getDatabase(application)
    private val chatDao: ChatDao = appDb.chatDao()
    private val metadataDao: ConversationMetadataDao = appDb.conversationMetadataDao()
    private val auth = FirebaseAuth.getInstance()

    // Current user ID from Firebase
    private val currentUserId: String
        get() = auth.currentUser?.uid ?: "local_user"

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

    private val rawConversationsFlow: Flow<List<ConversationInfo>> = chatDao.getConversationsForUser(currentUserId)
        .catch { e ->
            Log.e("ChatViewModel", "Error loading raw conversations flow", e)
            _errorMessage.value = "Erro ao carregar lista de conversas (raw)."
            emit(emptyList())
        }

    private val metadataFlow: Flow<List<ConversationMetadataEntity>> = metadataDao.getMetadataForUser(currentUserId)
        .catch { e ->
            Log.e("ChatViewModel", "Error loading metadata flow", e)
            emit(emptyList())
        }

    val conversationListForDrawer: StateFlow<List<ConversationDisplayItem>> =
        combine(rawConversationsFlow, metadataFlow) { conversations, metadataList ->
            Log.d("ChatViewModel", "Combining ${conversations.size} convs and ${metadataList.size} metadata entries.")
            val metadataMap = metadataList.associateBy({ it.conversationId }, { it.customTitle })

            conversations.map { convInfo ->
                val customTitle = metadataMap[convInfo.id]?.takeIf { it.isNotBlank() }
                val finalTitle = customTitle ?: generateFallbackTitle(convInfo.id)

                // Determinar o tipo de conversa com base no título ou no primeiro texto
                val conversationType = determineConversationType(finalTitle, convInfo.id)

                ConversationDisplayItem(
                    id = convInfo.id,
                    displayTitle = finalTitle,
                    lastTimestamp = convInfo.lastTimestamp,  // Garantir que está usando lastTimestamp
                    conversationType = conversationType
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .catch { e ->
                Log.e("ChatViewModel", "Error combining conversations and metadata", e)
                withContext(Dispatchers.Main.immediate) {
                    _errorMessage.value = "Erro ao processar lista de conversas para exibição."
                }
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

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

    private val welcomeMessageText = "Olá! 😊 Eu sou o Mente Sã, seu assistente virtual de saúde mental, e é um prazer te conhecer. Como você está se sentindo hoje? Estou aqui para te acompanhar com empatia e respeito, oferecendo um espaço seguro e acolhedor para você se expressar. Existe algo em particular que gostaria de conversar ou explorar?"

    private val menteSaSystemPrompt = """
    Você é o Mente Sã, um assistente virtual especializado exclusivamente em saúde mental, desenvolvido para oferecer suporte empático e baseado em evidências científicas.

    ## LIMITAÇÕES IMPORTANTES:
    1. Você DEVE se recusar a responder qualquer pergunta não relacionada à saúde mental.
    2. NÃO responda perguntas sobre física, química, matemática, história, geografia, política, esportes, entretenimento, tecnologia ou qualquer outro assunto não diretamente relacionado à saúde mental.
    3. Quando o usuário fizer uma pergunta fora do escopo, responda: "Desculpe, sou especializado apenas em temas de saúde mental. Posso ajudar você com questões relacionadas à ansiedade, depressão, técnicas de autocuidado ou outros assuntos ligados ao bem-estar emocional."
    4. Nunca forneça respostas parciais a tópicos fora do escopo, mesmo que pareçam ter alguma conexão com saúde mental.
    5. NUNCA forneça diagnósticos médicos. Sempre esclareça que suas informações não substituem avaliação profissional.
    
    ## TÓPICOS PERMITIDOS:
    - Transtornos mentais: ansiedade, depressão, transtorno bipolar, TOC, TEPT, e outros
    - Técnicas de meditação, mindfulness e gerenciamento de estresse
    - Métodos de autocuidado e promoção de bem-estar emocional
    - Comunicação saudável e desenvolvimento de relacionamentos interpessoais
    - Sono e sua relação com a saúde mental
    - Exercícios físicos e alimentação no contexto da saúde mental
    - Estratégias para regular emoções e desenvolver resiliência
    - Recursos e opções de tratamento para condições de saúde mental
    - Práticas de autocompaixão e desenvolvimento de autoestima saudável
    
    ## ESTILO DE RESPOSTA:
    - Seja empático e acolhedor, demonstrando compreensão das dificuldades do usuário
    - Use linguagem acessível e evite jargões técnicos desnecessários
    - Forneça informações precisas e baseadas em evidências científicas atualizadas
    - Ofereça sugestões práticas e aplicáveis ao contexto do usuário
    - Normalize as experiências de saúde mental para reduzir o estigma
    - Incentive a busca por ajuda profissional quando apropriado
    - Adapte o tom para ser reconfortante em momentos de crise e encorajador quando apropriado
    - Mantenha um equilíbrio entre validar sentimentos e oferecer perspectivas construtivas
    
    ## SITUAÇÕES DE RISCO:
    - Em casos de ideação suicida ou autolesão, responda com empatia e urgência
    - Direcione imediatamente para recursos de crise (como CVV - 188 no Brasil)
    - Nunca minimize esses sentimentos ou sugira que são "apenas uma fase"
    - Enfatize que ajuda está disponível e que sentimentos intensos são temporários

    Seu objetivo principal é criar um espaço seguro para discussões sobre saúde mental, oferecendo suporte informativo e emocional que promova o bem-estar do usuário.
""".trimIndent()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content { text(menteSaSystemPrompt) },
        requestOptions = RequestOptions(timeout = 60000)
    )

    init {
        loadInitialConversationOrStartNew()
    }

    // Função auxiliar para determinar o tipo de conversa
    private fun determineConversationType(title: String, id: Long): ConversationType {
        val lowercaseTitle = title.lowercase()

        return when {
            lowercaseTitle.contains("ansiedade") ||
                    lowercaseTitle.contains("medo") ||
                    lowercaseTitle.contains("preocup") -> ConversationType.EMOTIONAL

            lowercaseTitle.contains("depress") ||
                    lowercaseTitle.contains("triste") ||
                    lowercaseTitle.contains("terapia") ||
                    lowercaseTitle.contains("tratamento") -> ConversationType.THERAPEUTIC

            lowercaseTitle.contains("eu") ||
                    lowercaseTitle.contains("minha") ||
                    lowercaseTitle.contains("meu") ||
                    lowercaseTitle.contains("como me") -> ConversationType.PERSONAL

            lowercaseTitle.contains("importante") ||
                    lowercaseTitle.contains("urgente") ||
                    lowercaseTitle.contains("lembrar") -> ConversationType.HIGHLIGHTED

            else -> {
                // Distribuição aleatória mas determinística para o restante
                when ((id % 5)) {
                    0L -> ConversationType.GENERAL
                    1L -> ConversationType.PERSONAL
                    2L -> ConversationType.EMOTIONAL
                    3L -> ConversationType.THERAPEUTIC
                    else -> ConversationType.HIGHLIGHTED
                }
            }
        }
    }

    private fun loadInitialConversationOrStartNew() {
        viewModelScope.launch {
            delay(150)
            val initialDisplayList = conversationListForDrawer.value
            Log.d("ChatViewModel", "[Init] Initial display list check (using .value): ${initialDisplayList.size}")
            val latestConversationId = initialDisplayList.firstOrNull()?.id
            if (_currentConversationId.value == null) {
                _currentConversationId.value = latestConversationId ?: NEW_CONVERSATION_ID
                Log.i("ChatViewModel", "[Init] Setting initial conversation ID to: ${_currentConversationId.value}")
            } else {
                Log.d("ChatViewModel","[Init] Initial conversation ID already set to ${_currentConversationId.value}. Skipping.")
            }
        }
    }

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
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    metadataDao.insertOrUpdateMetadata(
                        ConversationMetadataEntity(
                            conversationId = targetConversationId,
                            customTitle = null,
                            userId = currentUserId
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error saving initial metadata for new conv $targetConversationId", e)
                }
            }
        }

        if (targetConversationId == null || targetConversationId == NEW_CONVERSATION_ID) {
            Log.e("ChatViewModel", "sendMessage Error: Invalid targetConversationId ($targetConversationId) after checking for new conversation.")
            _errorMessage.value = "Erro interno: Não foi possível determinar a conversa."
            _loadingState.value = LoadingState.IDLE
            return
        }

        val userUiMessage = ChatMessage(userMessageText, Sender.USER)
        saveMessageToDb(userUiMessage, targetConversationId, timestamp)

        if (this.isProhibitedTopic(userMessageText)) {
            Log.w("ChatViewModel", "Prohibited topic detected: '${userMessageText.take(50)}...'")

            val botResponse = "Desculpe, sou especializado apenas em temas de saúde mental. Posso ajudar você com ansiedade, depressão, técnicas de autocuidado ou outros assuntos relacionados ao bem-estar emocional."
            saveMessageToDb(ChatMessage(botResponse, Sender.BOT), targetConversationId)
            _loadingState.value = LoadingState.IDLE
            return
        }

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

    fun deleteConversation(conversationId: Long) {
        if (conversationId == NEW_CONVERSATION_ID) {
            Log.w("ChatViewModel", "Attempted to delete invalid NEW_CONVERSATION_ID conversation.")
            return
        }
        Log.i("ChatViewModel", "Action: Deleting conversation $conversationId and its metadata")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatDao.clearConversation(conversationId)
                metadataDao.deleteMetadata(conversationId)

                Log.i("ChatViewModel", "Conversation $conversationId and metadata deleted successfully from DB.")

                if (_currentConversationId.value == conversationId) {
                    val remainingConversations = chatDao.getConversationsForUser(currentUserId).first()

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
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error deleting conversation $conversationId or its metadata", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao excluir conversa: ${e.localizedMessage}"
                }
            }
        }
    }

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
        val metadata = ConversationMetadataEntity(
            conversationId = conversationId,
            customTitle = trimmedTitle,
            userId = currentUserId
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                metadataDao.insertOrUpdateMetadata(metadata)
                Log.i("ChatViewModel", "Conversation $conversationId renamed successfully in DB.")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error renaming conversation $conversationId", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao renomear conversa: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun isProhibitedTopic(message: String): Boolean {
        // Categorias de tópicos proibidos para facilitar manutenção
        val prohibitedTopics = mapOf(
            "Ciências Exatas" to listOf(
                "física", "química", "matemática", "equação", "fórmula", "cálculo",
                "átomo", "molécula", "reação", "elemento", "tabela periódica",
                "teorema", "álgebra", "geometria", "trigonometria", "derivada", "integral",
                "newton", "einstein", "celsius", "kelvin", "tesla", "feynman"
            ),
            "Astronomia" to listOf(
                "astronomia", "planeta", "galáxia", "sistema solar", "estrela", "universo",
                "nasa", "spacex", "satélite", "marte", "júpiter", "lua", "eclipse", "cometa"
            ),
            "História/Geografia" to listOf(
                "história", "geografia", "guerra", "império", "revolução", "dinastia",
                "continente", "país", "capital", "mapa", "relevo", "clima", "população",
                "guerra mundial", "idade média", "renascimento", "colonização", "civilização"
            ),
            "Política/Economia" to listOf(
                "política", "economia", "presidente", "eleição", "partido", "congresso",
                "senador", "deputado", "ministro", "inflação", "juros", "bolsa", "mercado",
                "imposto", "orçamento", "déficit", "superávit", "banco central", "governo"
            ),
            "Entretenimento" to listOf(
                "esporte", "futebol", "basquete", "vôlei", "filme", "novela", "série",
                "time", "campeonato", "copa", "olimpíada", "ator", "atriz", "diretor",
                "netflix", "cinema", "teatro", "show", "música", "concerto", "festival"
            ),
            "Tecnologia" to listOf(
                "computador", "programação", "código", "app", "desenvolvimento", "software",
                "hardware", "internet", "rede", "algoritmo", "linguagem", "java", "python",
                "website", "servidor", "banco de dados", "cloud", "api", "framework"
            ),
            "Linguística" to listOf(
                "língua", "gramática", "sintaxe", "semântica", "verbo", "substantivo",
                "pronome", "preposição", "ortografia", "fonética", "tradução", "idioma"
            )
        )

        // Converter a mensagem para minúsculo para comparação case-insensitive
        val lowercaseMessage = message.lowercase()

        // Verificar se a mensagem contém palavras-chave proibidas
        // Usando regex com limites de palavra para evitar falsos positivos
        return prohibitedTopics.values.flatten().any { keyword ->
            // Regex para corresponder à palavra completa ou parte de uma palavra composta
            val pattern = "\\b$keyword\\b|\\b$keyword-|\\b$keyword\\s"
            pattern.toRegex().containsMatchIn(lowercaseMessage)
        }
    }

    private fun isValidResponse(response: String): Boolean {
        // Padrões de frases que indicam que a resposta está fora do escopo
        val prohibitedPhrases = listOf(
            // Física
            "na física", "em física", "a física", "física é", "física clássica", "física quântica",
            "leis da física", "conceito físico", "fenômeno físico", "teoria física",

            // Matemática
            "em matemática", "na matemática", "fórmula", "equação", "cálculo de",
            "teorema", "matemática é", "matematicamente", "valor numérico", "resolva",

            // História
            "na história", "história do", "período histórico", "guerra mundial",
            "revolução", "império", "dinastia", "século", "era", "idade média",

            // Política
            "presidente", "governador", "político", "eleição", "partido",
            "congresso", "senado", "câmara", "ministro", "governo",

            // Astronomia
            "astronomia", "planeta", "sistema solar", "galáxia", "estrela",
            "constelação", "universo", "nasa", "telescópio", "órbita",

            // Tecnologia/Computação
            "computação", "programação", "código fonte", "algoritmo", "linguagem de programação",
            "software", "hardware", "aplicativo", "desenvolvimento web", "sistema operacional",

            // Esportes/Entretenimento
            "esporte", "time", "jogador", "campeonato", "liga", "filme", "série",
            "ator", "diretor", "cinema", "televisão", "streaming", "episódio"
        )

        // Exceções para permitir contextos de saúde mental
        val allowedContexts = listOf(
            "no contexto da saúde mental", "relacionado à saúde mental",
            "impacto na saúde mental", "afeta a saúde mental",
            "bem-estar emocional", "bem-estar psicológico",
            "técnica terapêutica", "abordagem terapêutica"
        )

        val lowercaseResponse = response.lowercase()

        // Verificar se há frases proibidas que não estão em contextos permitidos
        return !prohibitedPhrases.any { phrase ->
            // Verifica se contém a frase proibida
            if (lowercaseResponse.contains(phrase)) {
                // Verifica se está em um contexto permitido
                !allowedContexts.any { context ->
                    // Procura o contexto próximo à frase proibida (50 caracteres antes e depois)
                    val startIndex = maxOf(0, lowercaseResponse.indexOf(phrase) - 50)
                    val endIndex = minOf(lowercaseResponse.length, lowercaseResponse.indexOf(phrase) + phrase.length + 50)
                    val surroundingText = lowercaseResponse.substring(startIndex, endIndex)
                    surroundingText.contains(context)
                }
            } else {
                false
            }
        }
    }

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

                        if (!finalBotResponseText.isNullOrBlank() && !isValidResponse(finalBotResponseText!!)) {
                            Log.w("ChatViewModel", "Invalid response detected for conv $conversationId, replacing with fallback")
                            finalBotResponseText = "Desculpe, não posso fornecer essa informação pois está fora do escopo de saúde mental. Como posso ajudar com seu bem-estar emocional hoje?"
                        }
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
            timestamp = timestamp,
            userId = currentUserId
        )
    }

    private fun mapMessagesToApiHistory(messages: List<ChatMessage>): List<Content> {
        return messages
            .takeLast(MAX_HISTORY_MESSAGES)
            .map { msg ->
                val role = if (msg.sender == Sender.USER) "user" else "model"
                return@map content(role = role) { text(msg.text) }
            }
    }

    suspend fun getDisplayTitle(conversationId: Long): String {
        return withContext(Dispatchers.IO) {
            var titleResult: String
            if (conversationId == NEW_CONVERSATION_ID) {
                titleResult = "Nova Conversa"
            } else {
                try {
                    val customTitle = metadataDao.getCustomTitle(conversationId)
                    if (!customTitle.isNullOrBlank()) {
                        Log.d("ChatViewModel", "Using custom title for $conversationId: '$customTitle'")
                        titleResult = customTitle
                    } else {
                        titleResult = generateFallbackTitle(conversationId)
                    }
                } catch (dbException: Exception) {
                    Log.e("ChatViewModel", "Error fetching title data for conv $conversationId", dbException)
                    titleResult = "Conversa $conversationId"
                }
            }
            titleResult
        }
    }

    private suspend fun generateFallbackTitle(conversationId: Long): String = withContext(Dispatchers.IO) {
        try {
            val firstUserMessageText = chatDao.getFirstUserMessageText(conversationId)
            if (!firstUserMessageText.isNullOrBlank()) {
                Log.d("ChatViewModel", "Generating fallback title for $conversationId using first message.")
                return@withContext firstUserMessageText.take(30) + if (firstUserMessageText.length > 30) "..." else ""
            } else {
                try {
                    Log.d("ChatViewModel", "Generating fallback title for $conversationId using date.")
                    return@withContext "Conversa ${titleDateFormatter.format(Date(conversationId))}"
                } catch (formatException: Exception) {
                    Log.w("ChatViewModel", "Could not format conversationId $conversationId as Date for fallback title.", formatException)
                    return@withContext "Conversa $conversationId"
                }
            }
        } catch (dbException: Exception) {
            Log.e("ChatViewModel", "Error generating fallback title for conv $conversationId", dbException)
            return@withContext "Conversa $conversationId"
        }
    }

    companion object {
        private val titleDateFormatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
}