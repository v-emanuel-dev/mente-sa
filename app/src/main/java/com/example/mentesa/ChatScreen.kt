package com.example.mentesa

// Imports essenciais (verifique se todos estão presentes)
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
// Import do seu tema
import com.example.mentesa.ui.theme.MenteSaTheme
// Imports do Coroutines
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
// Imports das suas classes de dados/viewmodel (ajuste pacote se necessário)
import com.example.mentesa.ChatMessage
import com.example.mentesa.ChatUiState
import com.example.mentesa.ChatViewModel
import com.example.mentesa.LoadingState
import com.example.mentesa.Sender


@OptIn(ExperimentalMaterial3Api::class) // Necessário para TopAppBar/Scaffold
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel()
) {
    val chatUiState by chatViewModel.uiState.collectAsState()
    var userMessage by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            MessageInput(
                message = userMessage,
                onMessageChange = { userMessage = it },
                onSendClick = {
                    if (userMessage.isNotBlank()) {
                        chatViewModel.sendMessage(userMessage)
                        userMessage = ""
                    }
                },
                // Habilita/desabilita baseado no estado de loading
                isSendEnabled = chatUiState.loadingState != LoadingState.LOADING
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Aplica padding correto vindo do Scaffold
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f) // Ocupa espaço restante
                    .padding(horizontal = 16.dp),
                // Adiciona padding no topo e/ou fundo da lista se desejar
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
                // Exibe as mensagens da conversa
                items(chatUiState.messages) { message ->
                    MessageBubble(message = message)
                }

                // Exibe a animação de digitação se estiver carregando
                if (chatUiState.loadingState == LoadingState.LOADING) {
                    item {
                        TypingBubbleAnimation(modifier = Modifier.padding(bottom = 4.dp)) // Padding para espaçar da caixa de texto
                    }
                }
            }

            // Exibe mensagem de erro (opcional)
            if (chatUiState.loadingState == LoadingState.ERROR && chatUiState.errorMessage != null) {
                Text(
                    text = "Erro: ${chatUiState.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp) // Padding inferior
                )
            }
        }
    }

    // Efeito para rolar para o fim quando mensagens ou estado de loading mudam
    LaunchedEffect(chatUiState.messages.size, chatUiState.loadingState) {
        val itemCount = listState.layoutInfo.totalItemsCount
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }
}


/**
 * Composable para a barra de entrada de mensagem.
 */
@Composable
fun MessageInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isSendEnabled: Boolean
) {
    Surface(shadowElevation = 8.dp) { // Sombra para destacar a barra
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
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent
                ),
                enabled = isSendEnabled, // Controla se pode digitar
                maxLines = 5 // Permite múltiplas linhas
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Botão de enviar (sem indicador de progresso aqui)
            IconButton(
                onClick = onSendClick,
                enabled = message.isNotBlank() && isSendEnabled // Habilita se houver texto E não estiver carregando
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = stringResource(R.string.action_send),
                    tint = if (message.isNotBlank() && isSendEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}


/**
 * Composable para exibir uma única bolha de mensagem.
 * (Versão SIMPLIFICADA - sem lógica 'isPending')
 */
@Composable
fun MessageBubble(message: ChatMessage) {
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp), // Espaçamento vertical entre bolhas
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        SelectionContainer { // Permite copiar texto
            Box(
                modifier = Modifier
                    // Limita a largura da bolha para não ocupar a tela toda
                    .fillMaxWidth(0.8f) // Ex: máximo de 80% da largura
                    .wrapContentWidth(horizontalAlignment) // Alinha o Box em si
                    .clip(shape)
                    .background(bubbleColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp) // Padding interno
            ) {
                // Apenas exibe o texto
                Text(
                    text = message.text,
                    color = textColor
                )
            }
        }
    }
}


/**
 * Composable para a animação de "digitando" (3 pontos).
 */
@Composable
fun TypingIndicatorAnimation(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), // Ajuste alpha se quiser
    dotSize: Dp = 8.dp,
    spaceBetweenDots: Dp = 4.dp,
    bounceHeight: Dp = 6.dp
) {
    val dots = listOf(remember { Animatable(0f) }, remember { Animatable(0f) }, remember { Animatable(0f) })
    val bounceHeightPx = with(LocalDensity.current) { bounceHeight.toPx() }

    dots.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            delay(index * 140L) // Aumenta um pouco o delay para ficar mais suave
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000 // Duração um pouco maior
                        0f at 0 using LinearOutSlowInEasing
                        -bounceHeightPx at 250 using LinearOutSlowInEasing // Ajusta timing do pulo
                        0f at 500 using LinearOutSlowInEasing
                        0f at 1000
                    },
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        dots.forEachIndexed { index, animatable ->
            if (index != 0) {
                Spacer(modifier = Modifier.width(spaceBetweenDots))
            }
            Box(
                modifier = Modifier
                    .size(dotSize)
                    // Aplica a animação de deslocamento vertical
                    .graphicsLayer { translationY = animatable.value }
                    .background(color = dotColor, shape = CircleShape)
            )
        }
    }
}

/**
 * Composable que encapsula a animação de digitação dentro de uma bolha estilo BOT.
 */
@Composable
fun TypingBubbleAnimation(modifier: Modifier = Modifier) {
    val bubbleColor = MaterialTheme.colorScheme.surfaceVariant // Cor de fundo da bolha do BOT
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 16.dp)

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start // Alinha à esquerda
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth(Alignment.Start) // Garante que o box não estique desnecessariamente
                .defaultMinSize(minHeight = 40.dp) // Altura mínima para a bolha
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 16.dp, vertical = 10.dp), // Padding interno
            contentAlignment = Alignment.Center // Centraliza a animação
        ) {
            TypingIndicatorAnimation() // A animação dos 3 pontos
        }
    }
}


/**
 * Preview atualizado para usar TypingBubbleAnimation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showSystemUi = true)
@Composable
fun ChatScreenPreview() {
    val previewUiState = ChatUiState(
        messages = listOf(
            ChatMessage("Olá!", Sender.BOT),
            ChatMessage("Tudo bem?", Sender.USER),
        ),
        loadingState = LoadingState.LOADING // Simula estado de carregamento
    )

    MenteSaTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Mente Sã") },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White
                    )
                )
            },
            bottomBar = { MessageInput(message = "", onMessageChange = {}, onSendClick = {}, isSendEnabled = previewUiState.loadingState != LoadingState.LOADING) }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(previewUiState.messages) { msg ->
                    MessageBubble(message = msg)
                }
                // Mostra a bolha de digitação no preview se estiver carregando
                if (previewUiState.loadingState == LoadingState.LOADING) {
                    item { TypingBubbleAnimation() }
                }
            }
        }
    }
}