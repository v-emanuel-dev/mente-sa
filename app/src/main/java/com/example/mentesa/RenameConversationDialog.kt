package com.example.mentesa

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField // Usar OutlinedTextField para melhor visual
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties

/**
 * Composable para o diálogo de renomear conversa.
 *
 * @param conversationId O ID da conversa sendo renomeada.
 * @param currentTitle O título atual (opcional, para preencher o campo).
 * @param onConfirm Callback chamado ao confirmar, passando o ID e o novo título.
 * @param onDismiss Callback chamado quando o diálogo é dispensado.
 */
@Composable
fun RenameConversationDialog(
    conversationId: Long,
    currentTitle: String?, // Recebe o título atual para preencher o campo
    onConfirm: (id: Long, newTitle: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Estado para guardar o texto digitado no TextField
    // Usa `remember(currentTitle)` para resetar se o título inicial mudar (ex: abrir para outra conversa)
    var newTitleText by remember(currentTitle) { mutableStateOf(currentTitle ?: "") }
    // Estado para habilitar/desabilitar botão Confirmar
    val isConfirmEnabled by remember(newTitleText) {
        derivedStateOf { newTitleText.isNotBlank() }
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss, // Fecha ao clicar fora ou no botão Voltar
        title = { Text(stringResource(R.string.rename_conversation_dialog_title)) }, // Título do diálogo
        text = {
            // Coluna para organizar o TextField
            Column {
                OutlinedTextField(
                    value = newTitleText,
                    onValueChange = { newTitleText = it },
                    label = { Text(stringResource(R.string.new_conversation_name_label)) }, // Rótulo do campo
                    singleLine = true // Geralmente títulos são de uma linha
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Chama o callback passando o ID e o novo título (removendo espaços extras)
                    onConfirm(conversationId, newTitleText.trim())
                    // Não precisa chamar onDismiss() aqui, pois onConfirm na ChatScreen já fecha
                },
                // Habilita o botão só se o texto não estiver vazio
                enabled = isConfirmEnabled
            ) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}