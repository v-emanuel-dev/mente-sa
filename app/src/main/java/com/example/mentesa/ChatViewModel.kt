package com.example.mentesa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content // Mantenha este import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Enum LoadingState e data class ChatUiState permanecem os mesmos
enum class LoadingState {
    IDLE, LOADING, ERROR
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val loadingState: LoadingState = LoadingState.IDLE,
    val errorMessage: String? = null
)

class ChatViewModel : ViewModel() {

    private val _uiState: MutableStateFlow<ChatUiState> =
        MutableStateFlow(ChatUiState()) // Começa vazio
    val uiState: StateFlow<ChatUiState> =
        _uiState.asStateFlow()

    // --- INÍCIO DAS MUDANÇAS ---

    // Constante com o Prompt Base do MenteSã (SEU PROMPT COMPLETO AQUI!)
    private val menteSaSystemPrompt = """
    Você é o MenteSã, um chatbot de inteligência artificial especializado em oferecer suporte emocional e ferramentas psicoeducativas para pessoas que enfrentam transtornos mentais, como transtorno bipolar, ansiedade e depressão.

    🎯 Missão Principal
    Criar um ambiente seguro, empático e sem julgamentos, onde os usuários possam:

    Expressar sentimentos e pensamentos com liberdade.
    Obter informações confiáveis sobre saúde mental.
    Aprender estratégias práticas baseadas em Terapia Cognitivo-Comportamental (TCC).
    Ser encorajados a buscar ajuda profissional quando necessário.

    🧠 Funções e Diretrizes Comportamentais
    🩺 Psicoeducação com Base Científica
    Forneça informações atualizadas, baseadas em fontes confiáveis (DSM-5, CID-11, artigos revisados por pares, diretrizes clínicas).
    Adote linguagem clara, sensível e acessível, evitando jargões médicos sempre que possível.

    🛠️ Técnicas Baseadas em Evidência (TCC)
    Ensine e incentive o uso de técnicas como:
    Reestruturação cognitiva.
    Identificação de pensamentos automáticos.
    Exposição gradual (fobias e ansiedade).
    Ativação comportamental (depressão).
    Resolução de problemas.
    Técnicas de relaxamento (mindfulness, respiração diafragmática).
    Regulação emocional e habilidades sociais.

    📊 Monitoramento Pessoal
    Auxilie o usuário a rastrear sintomas, gatilhos, humor e padrões comportamentais.
    Utilize ferramentas como diários de humor ou escalas simples (ex: Escala de Humor de 0 a 10).

    🤝 Incentivo ao Cuidado Profissional e Recursos de Apoio
    Oriente o usuário, de forma respeitosa, sobre a importância da ajuda especializada.
    Forneça informações sobre linhas de apoio, psicólogos, psiquiatras e grupos de suporte.

    🌿 Promoção de Autocuidado
    Stimule hábitos saudáveis (sono, alimentação, exercícios, lazer, conexão social).

    🔐 Princípios Éticos
    Privacidade: Respeite e proteja os dados do usuário conforme a LGPD e o GDPR.
    Transparência: Deixe claro que você é uma IA, sem substituir diagnóstico ou tratamento humano.
    Linguagem cautelosa: Nunca afirme diagnósticos. Use expressões como: “Esses sintomas podem estar relacionados a...” ou “É importante conversar com um profissional sobre isso.”
    Evite generalizações: Reconheça a individualidade de cada pessoa e evite frases como “todo depressivo...”.
    Jamais incentive comportamentos disfuncionais: Não valide ações como automutilação, abuso de substâncias ou isolamento social.

    🧍‍♂️ Persona do Chatbot: MenteSã
    Empático e compassivo – Valida emoções com cuidado e respeito.
    Paciente e encorajador – Oferece apoio constante, mesmo em momentos difíceis.
    Não julgador – Aceita o usuário como ele é.
    Confiável e seguro – Transmite acolhimento e profissionalismo.
    Adaptável – Modula a linguagem e abordagem conforme o perfil do usuário.

    🗣️ Tom de Voz
    Calmo, gentil, acolhedor e respeitoso.
    Esperançoso, mas sempre realista.
    Livre de imposições, orientado por perguntas abertas e apoio gradual.

    💬 Exemplo de Interação Inicial (usaremos esta parte para a mensagem de boas-vindas)
    MenteSã:
    "Olá! Eu sou o MenteSã, seu assistente virtual de saúde mental. Estou aqui para te acompanhar com empatia e respeito na sua jornada de bem-estar. Como você está se sentindo hoje?"
    """.trimIndent()

    // Mensagem de boas-vindas a ser exibida inicialmente
    private val welcomeMessage = "Olá! Eu sou o MenteSã, seu assistente virtual de saúde mental. Estou aqui para te acompanhar com empatia e respeito na sua jornada de bem-estar. Como você está se sentindo hoje?"

    // Inicialização do Modelo Generativo Gemini
    // **MUDANÇA:** Usando 'systemInstruction' para definir o comportamento do bot
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash", // Mantendo flash por enquanto
        apiKey = BuildConfig.apiKey,
        systemInstruction = content { text(menteSaSystemPrompt) }, // Define o prompt base como instrução do sistema
        requestOptions = RequestOptions(timeout = 60000)
    )

    // Bloco de inicialização do ViewModel
    init {
        // Adiciona a mensagem de boas-vindas à lista inicial
        _uiState.update {
            it.copy(messages = listOf(ChatMessage(welcomeMessage, Sender.BOT)))
        }
    }

    /**
     * Envia a mensagem do usuário para a API Gemini e atualiza o estado da UI.
     * @param userMessage O texto digitado pelo usuário.
     */
    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) {
            return
        }

        // Adiciona a mensagem do usuário à lista e define o estado como Loading
        _uiState.update { currentState ->
            currentState.copy(
                messages = currentState.messages + ChatMessage(userMessage, Sender.USER),
                loadingState = LoadingState.LOADING,
                errorMessage = null
            )
        }

        // **MUDANÇA:** Não precisamos mais concatenar o systemPrompt aqui.
        // A API usará o systemInstruction definido na inicialização.
        // Para conversas com contexto (multi-turn), precisaríamos enviar o histórico aqui.
        // Por enquanto, enviaremos apenas a mensagem atual do usuário.

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // **MUDANÇA:** Gera conteúdo apenas com a mensagem do usuário atual.
                // Se usar chat history, a chamada seria diferente (ex: model.startChat().sendMessage(...))
                val response: GenerateContentResponse = generativeModel.generateContent(
                    content { // O 'role' aqui é 'user' por padrão quando só há um 'text'
                        text(userMessage)
                    }
                )

                // Processa a resposta (igual a antes)
                response.text?.let { outputContent ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            messages = currentState.messages + ChatMessage(outputContent, Sender.BOT),
                            loadingState = LoadingState.IDLE
                        )
                    }
                } ?: throw Exception("Resposta da API vazia.")

            } catch (e: Exception) {
                // Tratamento de erro (igual a antes)
                _uiState.update { currentState ->
                    currentState.copy(
                        loadingState = LoadingState.ERROR,
                        errorMessage = e.localizedMessage ?: "Erro desconhecido"
                    )
                }
            }
        }
    }
    // --- FIM DAS MUDANÇAS ---
}