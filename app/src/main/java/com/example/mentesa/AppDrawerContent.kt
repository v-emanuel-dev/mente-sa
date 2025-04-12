package com.example.mentesa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
        Text(
            text = stringResource(R.string.conversation_list_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text(stringResource(R.string.new_conversation_title)) },
            selected = currentConversationId == null || currentConversationId == NEW_CONVERSATION_ID,
            onClick = onNewChatClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        Spacer(modifier = Modifier.height(8.dp))

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
    } else {
        Color.Transparent
    }

    // Determina o ícone da conversa com base no seu tipo
    val conversationType = displayItem.conversationType ?: ConversationType.GENERAL
    val (conversationIcon, iconTint) = when (conversationType) {
        ConversationType.PERSONAL -> Icons.Default.Person to MaterialTheme.colorScheme.secondary
        ConversationType.EMOTIONAL -> Icons.Default.Favorite to Color(0xFFE57373) // Vermelho claro
        ConversationType.THERAPEUTIC -> Icons.Default.Psychology to Color(0xFF64B5F6) // Azul claro
        ConversationType.HIGHLIGHTED -> Icons.Default.Star to Color(0xFFFFD54F) // Amarelo âmbar
        ConversationType.GENERAL -> Icons.Default.Chat to MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = conversationIcon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = iconTint
        )
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
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.conversation_options_desc))
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
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_action)) },
                    onClick = {
                        onDeleteClick()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }
    }
}