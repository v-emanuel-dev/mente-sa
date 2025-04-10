package com.example.mentesa

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mentesa.ui.theme.MenteSaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
// Import da nova Data Class para a UI do Drawer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import dev.jeziellago.compose.markdowntext.MarkdownText

// Definindo as cores personalizadas
private val UserBubbleColor = Color(0xFF303030) // #303030 para o usuário
private val BotBubbleColor = Color(0xFF000000)  // #171717 para o bot
private val UserTextColor = Color.White         // Cor do texto nas bolhas do usuário
private val BotTextColor = Color.White          // Cor do texto nas bolhas do bot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel()
) {
    // Coleta estados do ViewModel
    val messages by chatViewModel.messages.collectAsState()
    val conversationDisplayList by chatViewModel.conversationListForDrawer.collectAsState()
    val currentConversationId by chatViewModel.currentConversationId.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val errorMessage by chatViewModel.errorMessage.collectAsState()

    // Estados da UI
    var userMessage by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Estados para diálogos
    var conversationIdToRename by remember { mutableStateOf<Long?>(null) }
    var currentTitleForDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmationDialog by remember { mutableStateOf<Long?>(null) }

    // Efeito para buscar título para o diálogo de renomear
    LaunchedEffect(conversationIdToRename) {
        val id = conversationIdToRename
        currentTitleForDialog = if (id != null && id != NEW_CONVERSATION_ID) "" else null
        if (id != null && id != NEW_CONVERSATION_ID) {
            Log.d("ChatScreen", "Fetching title for rename dialog (ID: $id)")
            try {
                currentTitleForDialog = chatViewModel.getDisplayTitle(id)
            } catch (e: Exception) {
                Log.e("ChatScreen", "Error fetching title for rename dialog", e)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                conversationDisplayItems = conversationDisplayList,
                currentConversationId = currentConversationId,
                onConversationClick = { conversationId ->
                    coroutineScope.launch { drawerState.close() }
                    if (conversationId != currentConversationId) {
                        chatViewModel.selectConversation(conversationId)
                    }
                },
                onNewChatClick = {
                    coroutineScope.launch { drawerState.close() }
                    if (currentConversationId != null && currentConversationId != NEW_CONVERSATION_ID) {
                        chatViewModel.startNewConversation()
                    }
                },
                onDeleteConversationRequest = { conversationId ->
                    // Não fecha mais o drawer aqui
                    showDeleteConfirmationDialog = conversationId
                },
                onRenameConversationRequest = { conversationId ->
                    // Não fecha mais o drawer aqui
                    Log.d("ChatScreen", "Rename requested for $conversationId. Setting state.")
                    conversationIdToRename = conversationId
                }
            )
        }
    ) { // Conteúdo principal (Scaffold)
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(id = R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon( Icons.Filled.Menu, stringResource(R.string.open_drawer_description), tint = Color.White )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black, titleContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                // Chama MessageInput (que agora tem padding interno)
                MessageInput(
                    message = userMessage,
                    onMessageChange = { userMessage = it },
                    onSendClick = {
                        if (userMessage.isNotBlank()) {
                            chatViewModel.sendMessage(userMessage)
                            userMessage = ""
                        }
                    },
                    isSendEnabled = !isLoading
                )
            }
        ) { paddingValues ->
            Column( modifier = Modifier.fillMaxSize().padding(paddingValues) ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp) // Padding interno da lista
                ) {
                    items(messages, key = { "${it.sender}-${it.text.hashCode()}" }) { message ->
                        MessageBubble(message = message)
                    }
                    if (isLoading) {
                        item { TypingBubbleAnimation(modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
                errorMessage?.let { errorMsg ->
                    Text(
                        text = "Erro: $errorMsg",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)).padding(vertical = 4.dp)
                    )
                }
            }
        } // Fim Scaffold

        // --- Diálogos ---
        showDeleteConfirmationDialog?.let { conversationIdToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmationDialog = null },
                title = { Text(stringResource(R.string.delete_confirmation_title)) },
                text = { Text(stringResource(R.string.delete_confirmation_text)) },
                confirmButton = { TextButton( onClick = { chatViewModel.deleteConversation(conversationIdToDelete); showDeleteConfirmationDialog = null } ) { Text(stringResource(R.string.delete_confirm_button), color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { showDeleteConfirmationDialog = null }) { Text(stringResource(R.string.cancel_button)) } }
            )
        }

        conversationIdToRename?.let { id ->
            if (currentTitleForDialog != null) {
                RenameConversationDialog(
                    conversationId = id,
                    currentTitle = currentTitleForDialog,
                    onConfirm = { confirmedId, newTitle ->
                        chatViewModel.renameConversation(confirmedId, newTitle)
                        conversationIdToRename = null
                    },
                    onDismiss = {
                        conversationIdToRename = null
                    }
                )
            }
        }

    } // Fim ModalNavigationDrawer

    // Efeito para rolar a lista de mensagens
    LaunchedEffect(messages.size, isLoading) { /* ... */ }

} // Fim ChatScreen


// ========================================================
// Demais Composables DEFINIDOS DENTRO DE ChatScreen.kt
// ========================================================

@Composable
fun MessageInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isSendEnabled: Boolean
) {
    // --- ALTERAÇÃO APLICADA AQUI: Adicionado padding inferior ao Surface ---
    Surface(
        shadowElevation = 8.dp,
        modifier = Modifier.padding(bottom = 8.dp) // <<-- Adiciona espaço ABAIXO do input
    ) {
        // --- FIM DA ALTERAÇÃO ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Padding interno do Row
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = message,
                onValueChange = onMessageChange,
                placeholder = { Text(stringResource(R.string.message_input_placeholder)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                enabled = isSendEnabled,
                maxLines = 5
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendClick,
                enabled = message.isNotBlank() && isSendEnabled,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.action_send),
                    tint = if (message.isNotBlank() && isSendEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUserMessage = message.sender == Sender.USER
    val bubbleAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart

    // Usando as cores personalizadas para o usuário (bot não terá mais bolha)
    val userBubbleColor = UserBubbleColor
    val userTextColor = UserTextColor
    val botTextColor = BotTextColor // Cor do texto do bot sem bolha

    val userShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 0.dp
    )

    // Estado de animação (apenas para mensagens do bot)
    val visibleState = remember { MutableTransitionState(initialState = isUserMessage) }

    // Iniciar animação se for mensagem do bot
    LaunchedEffect(message) {
        if (!isUserMessage) {
            visibleState.targetState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUserMessage) {
            // Mensagem do usuário - mantém a bolha como estava
            SelectionContainer {
                Text(
                    text = message.text,
                    color = userTextColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.75f)
                        .clip(userShape)
                        .background(userBubbleColor)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        } else {
            // Mensagem do bot - sem bolha, apenas o texto ocupando toda a largura
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
                        slideInHorizontally(
                            initialOffsetX = { -40 },
                            animationSpec = tween(durationMillis = 400)
                        )
            ) {
                // Container para o conteúdo da mensagem do bot - sem background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Renderização de markdown sem bolha
                    MarkdownText(
                        markdown = message.text,
                        color = botTextColor,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        modifier = Modifier.fillMaxWidth(),
                        linkColor = Color(0xFF90CAF9), // Azul claro para links
                        onClick = { /* Trate cliques em links, se necessário */ }
                    )
                }
            }
        }
    }
}

// Exemplo de modelo de dados expandido para suportar metadados de formatação
// Você pode adicionar isso ao seu arquivo de modelo ChatMessage existente
/*
data class ChatMessage(
    val text: String,
    val sender: Sender,
    val timestamp: Long = System.currentTimeMillis(),
    val isMarkdown: Boolean = false // Opcional: você pode usar isso para determinar se o texto deve ser renderizado como markdown
)
*/

// Para processamento de mensagens da API, talvez você queira adicionar uma função de utilidade:
/*
fun processApiResponse(apiResponse: ApiResponse): ChatMessage {
    return ChatMessage(
        text = apiResponse.content,
        sender = Sender.BOT,
        isMarkdown = true // Considera todas as mensagens da API como markdown
    )
}
*/


@Composable
fun TypingIndicatorAnimation(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    dotSize: Dp = 8.dp,
    spaceBetweenDots: Dp = 4.dp,
    bounceHeight: Dp = 6.dp
) {
    val dots = listOf(remember { Animatable(0f) }, remember { Animatable(0f) }, remember { Animatable(0f) })
    val bounceHeightPx = with(LocalDensity.current) { bounceHeight.toPx() }

    dots.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            delay(index * 140L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0f at 0 using LinearOutSlowInEasing
                        bounceHeightPx at 250 using LinearOutSlowInEasing
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
                    .graphicsLayer { translationY = animatable.value }
                    .background(color = dotColor, shape = CircleShape)
            )
        }
    }
}


@Composable
fun TypingBubbleAnimation(modifier: Modifier = Modifier) {
    val bubbleColor = MaterialTheme.colorScheme.secondaryContainer
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = modifier
                .wrapContentWidth(Alignment.Start)
                .defaultMinSize(minHeight = 40.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            TypingIndicatorAnimation()
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview(showSystemUi = true, name = "Chat Screen Preview")
@Composable
fun ChatScreenPreview() {
    val previewMessages = remember { mutableStateListOf( ChatMessage("Olá! Preview.", Sender.BOT), ChatMessage("Tudo bem?", Sender.USER) ) }
    val previewConversations = remember { listOf( ConversationDisplayItem(1L, "Conversa 1", 0L), ConversationDisplayItem(2L, "Conversa 2 Longa...", 0L) ) }
    val previewIsLoading = remember { mutableStateOf(false) }

    MenteSaTheme {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawerContent(
                    conversationDisplayItems = previewConversations,
                    currentConversationId = 1L,
                    onConversationClick = { scope.launch { drawerState.close() } },
                    onNewChatClick = { scope.launch { drawerState.close() } },
                    onDeleteConversationRequest = {},
                    onRenameConversationRequest = {}
                )
            }
        ) {
            Scaffold(
                topBar = { CenterAlignedTopAppBar( title = { Text("Mente Sã Preview") }, navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Filled.Menu, "") } } ) },
                bottomBar = { MessageInput( message = "", onMessageChange = {}, onSendClick = {}, isSendEnabled = true ) }
            ) { padding ->
                LazyColumn( modifier = Modifier.padding(padding).fillMaxSize() ) {
                    items(previewMessages) { msg -> MessageBubble(message = msg) }
                    if (previewIsLoading.value) { item { TypingBubbleAnimation() } }
                }
            }
        }
    }
}