package com.example.mentesa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
// Import necessário para a funcionalidade de copiar
import androidx.compose.foundation.text.selection.SelectionContainer
// Import do tema (para o Preview e potencialmente dentro da função principal)
import com.example.mentesa.ui.theme.MenteSaTheme

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel() // Usa o ViewModel renomeado
) {
    // Coleta o estado atual da UI do ViewModel
    val chatUiState by chatViewModel.uiState.collectAsState()
    // Estado para guardar o texto que o usuário está digitando
    var userMessage by rememberSaveable { mutableStateOf("") }
    // Estado para controlar a rolagem da lista
    val listState = rememberLazyListState()
    // Scope para lançar coroutines (para rolar a lista)
    val coroutineScope = rememberCoroutineScope()

    // Estrutura principal da tela usando Scaffold (padrão Material Design)
    Scaffold(
        // Barra inferior que contém o campo de texto e o botão de enviar
        bottomBar = {
            MessageInput(
                message = userMessage,
                onMessageChange = { userMessage = it },
                onSendClick = {
                    if (userMessage.isNotBlank()) {
                        chatViewModel.sendMessage(userMessage)
                        userMessage = "" // Limpa o campo após enviar
                        // Rola para o fim da lista após enviar (opcional, mas bom para UX)
                        coroutineScope.launch {
                            // Espera um pouco para a nova mensagem ser adicionada e rola
                            // Usar .size pode ser impreciso se a lista atualiza rápido, mas é simples
                            listState.animateScrollToItem(chatUiState.messages.size)
                        }
                    }
                },
                // Desabilita input enquanto aguarda resposta
                isSendingEnabled = chatUiState.loadingState == LoadingState.IDLE
            )
        }
    ) { paddingValues -> // Conteúdo principal da tela (lista de mensagens)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Aplica o padding do Scaffold
        ) {
            // Lista rolável de mensagens
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f) // Ocupa todo o espaço disponível acima da barra de input
                    .padding(horizontal = 16.dp),
                // verticalArrangement = Arrangement.Bottom // Comum em chats, mas pode causar saltos com teclado
                // Prefira rolar manualmente para o fim
            ) {
                // Itera sobre a lista de mensagens do UiState
                items(chatUiState.messages) { message ->
                    // Exibe a bolha de mensagem apropriada (Usuário ou Bot)
                    MessageBubble(message = message)
                }

                // Se estiver carregando, mostra um indicador na posição do BOT
                if (chatUiState.loadingState == LoadingState.LOADING) {
                    item {
                        MessageBubble(ChatMessage("...", Sender.BOT, isPending = true))
                    }
                }
            }

            // Exibe mensagem de erro (opcional, poderia ser um Snackbar ou Toast)
            if (chatUiState.loadingState == LoadingState.ERROR && chatUiState.errorMessage != null) {
                Text(
                    text = "Erro: ${chatUiState.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    // Efeito para rolar para o fim quando uma nova mensagem chega (do user ou bot)
    LaunchedEffect(chatUiState.messages.size) {
        // Verifica se a lista não está vazia antes de tentar rolar
        if (chatUiState.messages.isNotEmpty()) {
            // Rola para o último item adicionado
            listState.animateScrollToItem(chatUiState.messages.size - 1)
        }
    }
}

/**
 * Composable para a barra de entrada de mensagem na parte inferior.
 */
@Composable
fun MessageInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isSendingEnabled: Boolean // Controla se o input/botão estão ativos
) {
    Surface(shadowElevation = 8.dp) { // Adiciona uma leve sombra para destacar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = message,
                onValueChange = onMessageChange,
                placeholder = { Text("Digite sua mensagem...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp), // Bordas arredondadas
                colors = TextFieldDefaults.colors( // Remove a linha indicadora padrão
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent // Também para erro
                ),
                enabled = isSendingEnabled, // Desabilita enquanto carrega
                maxLines = 5 // Permite algumas linhas mas não infinitas
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendClick,
                enabled = message.isNotBlank() && isSendingEnabled // Habilita só se houver texto e não estiver carregando
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = stringResource(R.string.action_send), // Certifique-se que R.string.action_send existe
                    tint = if (message.isNotBlank() && isSendingEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}

/**
 * Composable para exibir uma única bolha de mensagem.
 * Adapta a aparência com base no remetente.
 * AGORA COM SUPORTE A SELEÇÃO DE TEXTO PARA COPIAR.
 */
@Composable
fun MessageBubble(message: ChatMessage) {
    // Determina o alinhamento e a cor com base no remetente
    val isUserMessage = message.sender == Sender.USER
    val horizontalAlignment = if (isUserMessage) Alignment.End else Alignment.Start
    val bubbleColor = if (isUserMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUserMessage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUserMessage) 16.dp else 0.dp,
        bottomEnd = if (isUserMessage) 0.dp else 16.dp
    )

    // Organiza a bolha na linha, alinhada corretamente
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        // --- MUDANÇA APLICADA AQUI ---
        // Envolve a bolha com SelectionContainer para habilitar copiar/colar
        SelectionContainer {
            // A bolha de mensagem real
            Box(
                modifier = Modifier
                    // Limita a largura máxima da bolha
                    .fillMaxWidth(if (message.isPending) 0.2f else 0.8f) // Largura menor para o indicador de pendente
                    .wrapContentWidth(horizontalAlignment) // Garante que o Box em si alinhe corretamente
                    .clip(shape)
                    .background(bubbleColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Texto da mensagem ou indicador de pendente
                if (message.isPending) {
                    // Indicador de "digitando..." simples
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.Center),
                        strokeWidth = 2.dp,
                        color = textColor // Usa a cor do texto para o indicador
                    )
                } else {
                    Text(
                        text = message.text,
                        color = textColor
                    )
                }
            }
        }
        // --- FIM DA MUDANÇA ---
    }
}

// Preview para o ChatScreen (atualizado)
@Preview(showSystemUi = true)
@Composable
fun ChatScreenPreview() {
    // Fornece um estado de exemplo para o preview
    val previewUiState = ChatUiState(
        messages = listOf(
            ChatMessage("Olá! Como posso ajudar?", Sender.BOT),
            ChatMessage("Estou me sentindo um pouco ansioso hoje.", Sender.USER),
            ChatMessage("Entendo. Respire fundo. Quer tentar um exercício de respiração?", Sender.BOT)
        )
    )

    // É importante envolver o preview com o seu tema
    MenteSaTheme {
        // Simula a estrutura do Scaffold para o preview
        Scaffold(
            bottomBar = { MessageInput(message = "Test message", onMessageChange = {}, onSendClick = {}, isSendingEnabled = true) }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                state = rememberLazyListState(), // Precisa de um estado para o preview
                contentPadding = PaddingValues(horizontal = 16.dp) // Padding para a lista
            ) {
                items(previewUiState.messages) { msg ->
                    MessageBubble(message = msg)
                }
                // Adiciona um preview do estado de loading
                item {
                    MessageBubble(ChatMessage("...", Sender.BOT, isPending = true))
                }
            }
        }
    }
}