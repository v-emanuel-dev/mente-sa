package com.example.mentesa

import android.util.Log
import androidx.compose.foundation.background // Adicionar import
import androidx.compose.foundation.clickable // Adicionar import
import androidx.compose.foundation.layout.* // Adicionar imports (Row, Spacer, Box, etc.)
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreVert // Ícone "..."
import androidx.compose.material.icons.filled.Delete // Ícone Lixeira
import androidx.compose.material.icons.filled.Edit // Ícone Editar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment // Adicionar import
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Adicionar import
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mentesa.data.db.ConversationInfo
import com.example.mentesa.NEW_CONVERSATION_ID // Importa constante

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerContent(
    conversations: List<ConversationInfo>,
    currentConversationId: Long?,
    viewModel: ChatViewModel, // Ainda necessário para getDisplayTitle nesta versão
    onConversationClick: (Long) -> Unit,
    onNewChatClick: () -> Unit,
    // NOVAS LAMBDAS PARA AÇÕES
    onDeleteConversationRequest: (conversationId: Long) -> Unit,
    onRenameConversationRequest: (conversationId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        // Título do Drawer
        Text(
            text = stringResource(R.string.conversation_list_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()

        // Item "Nova Conversa"
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.AddComment, contentDescription = null) },
            label = { Text(stringResource(R.string.new_conversation_title)) },
            selected = currentConversationId == null || currentConversationId == NEW_CONVERSATION_ID,
            onClick = onNewChatClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Lista de Conversas Existentes
        if (conversations.isEmpty()) {
            Text(
                text = stringResource(R.string.no_conversations_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                items(conversations, key = { it.id }) { conversationInfo ->
                    // Usa o novo Composable de linha customizado
                    ConversationDrawerRow(
                        conversationInfo = conversationInfo,
                        isSelected = conversationInfo.id == currentConversationId,
                        viewModel = viewModel,
                        onItemClick = { onConversationClick(conversationInfo.id) },
                        onRenameClick = { onRenameConversationRequest(conversationInfo.id) },
                        onDeleteClick = { onDeleteConversationRequest(conversationInfo.id) }
                    )
                    HorizontalDivider() // Divisor entre itens
                }
            }
        }
    }
}

// NOVO: Composable para a linha da conversa com opções
@Composable
private fun ConversationDrawerRow(
    conversationInfo: ConversationInfo,
    isSelected: Boolean,
    viewModel: ChatViewModel,
    onItemClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val loadingPlaceholder = stringResource(R.string.loading_title_placeholder)
    val errorLoadingText = stringResource(R.string.error_loading_title)
    val displayTitleState = produceState(loadingPlaceholder, conversationInfo.id) {
        value = try {
            viewModel.getDisplayTitle(conversationInfo.id)
        } catch (e: Exception) {
            Log.e("AppDrawerContent", "Error fetching title for conv ${conversationInfo.id}", e)
            errorLoadingText
        }
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick) // Linha clicável
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = displayTitleState.value,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f) // Empurra o botão para a direita
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Box para ancorar o DropdownMenu
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.conversation_options_desc) // String para acessibilidade
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename_action)) },
                    onClick = {
                        onRenameClick()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_action)) },
                    onClick = {
                        onDeleteClick()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                )
            }
        }
    }
}