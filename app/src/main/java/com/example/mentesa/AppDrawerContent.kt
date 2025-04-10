package com.example.mentesa

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.* // Necessário para Spacer, etc.
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mentesa.ConversationDisplayItem
import com.example.mentesa.NEW_CONVERSATION_ID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerContent(
    conversationDisplayItems: List<ConversationDisplayItem>,
    currentConversationId: Long?,
    onConversationClick: (Long) -> Unit,
    onNewChatClick: () -> Unit,
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

        // --- ADICIONADO SPACER AQUI ---
        Spacer(modifier = Modifier.height(8.dp)) // Adiciona espaço acima do botão
        // --- FIM DA ADIÇÃO ---

        // Item "Nova Conversa"
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.AddComment, contentDescription = null) },
            label = { Text(stringResource(R.string.new_conversation_title)) },
            selected = currentConversationId == null || currentConversationId == NEW_CONVERSATION_ID,
            onClick = onNewChatClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding) // Padding padrão do item
        )
        Spacer(modifier = Modifier.height(8.dp)) // Mantém espaço abaixo também

        // Lista de Conversas Existentes
        if (conversationDisplayItems.isEmpty()) {
            Text(
                text = stringResource(R.string.no_conversations_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                items(conversationDisplayItems, key = { it.id }) { displayItem ->
                    ConversationDrawerRow(
                        displayItem = displayItem,
                        isSelected = displayItem.id == currentConversationId,
                        onItemClick = { onConversationClick(displayItem.id) },
                        onRenameClick = { onRenameConversationRequest(displayItem.id) },
                        onDeleteClick = { onDeleteConversationRequest(displayItem.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ConversationDrawerRow(
    displayItem: ConversationDisplayItem,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else { Color.Transparent }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon( Icons.Default.Chat, null, Modifier.size(24.dp) )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = displayItem.displayTitle,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon( Icons.Default.MoreVert, stringResource(R.string.conversation_options_desc) )
            }
            DropdownMenu( expanded = showMenu, onDismissRequest = { showMenu = false } ) {
                DropdownMenuItem( text = { Text(stringResource(R.string.rename_action)) }, onClick = { onRenameClick(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Edit, null) } )
                DropdownMenuItem( text = { Text(stringResource(R.string.delete_action)) }, onClick = { onDeleteClick(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) } )
            }
        }
    }
}