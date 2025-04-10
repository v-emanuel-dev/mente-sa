package com.example.mentesa

import android.util.Log
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
import androidx.compose.material.icons.filled.Menu
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
import com.example.mentesa.ui.theme.MenteSaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.mentesa.data.db.ConversationInfo
import com.example.mentesa.ChatMessage
import com.example.mentesa.ChatViewModel
import com.example.mentesa.LoadingState
import com.example.mentesa.Sender
// Imports para Diálogo (se for usar)
// import androidx.compose.material3.AlertDialog
// import androidx.compose.material3.TextButton


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel()
) {
    // Coleta estados do ViewModel
    val messages by chatViewModel.messages.collectAsState()
    val conversations by chatViewModel.conversations.collectAsState()
    val currentConversationId by chatViewModel.currentConversationId.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val errorMessage by chatViewModel.errorMessage.collectAsState()

    // Estados da UI
    var userMessage by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Estado para diálogo de confirmação de exclusão (opcional)
    var showDeleteConfirmationDialog by remember { mutableStateOf<Long?>(null) } // Guarda ID a deletar ou null

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                conversations = conversations,
                currentConversationId = currentConversationId,
                viewModel = chatViewModel,
                onConversationClick = { conversationId ->
                    // Fecha drawer e seleciona conversa
                    coroutineScope.launch { drawerState.close() }
                    if (conversationId != currentConversationId) {
                        chatViewModel.selectConversation(conversationId)
                    }
                },
                onNewChatClick = {
                    // Fecha drawer e inicia nova conversa
                    coroutineScope.launch { drawerState.close() }
                    if (currentConversationId != null && currentConversationId != NEW_CONVERSATION_ID) {
                        chatViewModel.startNewConversation()
                    }
                },
                // --- Passando as novas lambdas ---
                onDeleteConversationRequest = { conversationId ->
                    chatViewModel.deleteConversation(conversationId)
                },
                onRenameConversationRequest = { conversationId ->
                    // Fecha o drawer e prepara para renomear (mostra diálogo - a implementar)
                    coroutineScope.launch { drawerState.close() }
                    // TODO: Implementar lógica para mostrar diálogo de renomear
                    Log.d("ChatScreen", "Rename requested for $conversationId")
                    // Ex: showRenameDialogForId = conversationId
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
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.open_drawer_description),
                                tint = Color.White
                            )
                        }
                    },
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
                    isSendEnabled = !isLoading
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues) // Aplica padding correto vindo do Scaffold
            ) {
                // Lista de Mensagens
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                ) {
                    items(messages, key = { it.hashCode() }) { message ->
                        MessageBubble(message = message)
                    }
                    // Indicador de "Digitando..."
                    if (isLoading) {
                        item {
                            TypingBubbleAnimation(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                } // Fim LazyColumn

                // Mensagem de Erro
                errorMessage?.let { errorMsg ->
                    Text(
                        text = "Erro: $errorMsg",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))
                            .padding(vertical = 4.dp)
                    )
                }
            } // Fim Column principal
        } // Fim Scaffold

        // --- Diálogo de Confirmação de Exclusão (Opcional) ---
        // Se showDeleteConfirmationDialog não for nulo, mostra o diálogo
        showDeleteConfirmationDialog?.let { conversationIdToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmationDialog = null }, // Fecha ao clicar fora
                title = { Text(stringResource(R.string.delete_confirmation_title)) },
                text = { Text(stringResource(R.string.delete_confirmation_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            chatViewModel.deleteConversation(conversationIdToDelete)
                            showDeleteConfirmationDialog = null // Fecha o diálogo
                        }
                    ) {
                        Text(stringResource(R.string.delete_confirm_button), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmationDialog = null }) {
                        Text(stringResource(R.string.cancel_button))
                    }
                }
            )
        }

    } // Fim ModalNavigationDrawer

    // Efeito para rolar a lista de mensagens
    LaunchedEffect(messages.size, isLoading) {
        val itemCount = listState.layoutInfo.totalItemsCount
        if (itemCount > 0) {
            delay(100)
            listState.animateScrollToItem(itemCount - 1)
        }
    }
} // Fim ChatScreen


// ========================================================
// Demais Composables de ChatScreen (MessageInput, MessageBubble, etc.)
// ========================================================

@Composable
fun MessageInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isSendEnabled: Boolean
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    errorIndicatorColor = Color.Transparent
                ),
                enabled = isSendEnabled,
                maxLines = 5
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendClick,
                enabled = message.isNotBlank() && isSendEnabled
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
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .wrapContentWidth(horizontalAlignment)
                    .clip(shape)
                    .background(bubbleColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun TypingIndicatorAnimation( /* ...código sem alterações... */ ) {
    val dots = listOf(remember { Animatable(0f) }, remember { Animatable(0f) }, remember { Animatable(0f) })
    val bounceHeightPx = with(LocalDensity.current) { 6.dp.toPx() } // Exemplo, use suas variáveis

    dots.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            delay(index * 140L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0f at 0 using LinearOutSlowInEasing
                        -bounceHeightPx at 250 using LinearOutSlowInEasing
                        0f at 500 using LinearOutSlowInEasing
                        0f at 1000
                    },
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Row(
        modifier = Modifier, // Use o modifier passado
        verticalAlignment = Alignment.CenterVertically
    ) {
        dots.forEachIndexed { index, animatable ->
            if (index != 0) {
                Spacer(modifier = Modifier.width(4.dp)) // Use suas variáveis
            }
            Box(
                modifier = Modifier
                    .size(8.dp) // Use suas variáveis
                    .graphicsLayer { translationY = animatable.value }
                    .background(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), shape = CircleShape) // Use suas variáveis
            )
        }
    }
}

@Composable
fun TypingBubbleAnimation(modifier: Modifier = Modifier) {
    val bubbleColor = MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 16.dp)

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
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

// Preview pode precisar de ajustes para refletir a nova estrutura do drawer,
// mas mantenha-o como estava ou simplifique-o se estiver causando problemas.
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showSystemUi = true, name = "Chat Screen Preview")
@Composable
fun ChatScreenPreview() {
    // ... (Mantenha ou ajuste seu preview existente) ...
    // Lembre-se que previews não interagem com o ViewModel real ou DB.
    MenteSaTheme {
        Text("Preview da Tela de Chat (Simplificado)") // Placeholder simples
    }
}