package com.example.mentesa.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert // Upsert (Update/Insert) é ideal para salvar/atualizar título
import kotlinx.coroutines.flow.Flow

/**
 * DAO para acessar os metadados das conversas (títulos personalizados).
 */
@Dao
interface ConversationMetadataDao {

    /**
     * Insere ou atualiza os metadados de uma conversa.
     * Se já existir metadados para o conversationId, o título será atualizado;
     * caso contrário, uma nova linha será inserida.
     */
    @Upsert
    suspend fun insertOrUpdateMetadata(metadata: ConversationMetadataEntity)

    /**
     * Busca o título personalizado para uma conversa específica.
     * Retorna o título (String) ou null se não houver título personalizado definido.
     */
    @Query("SELECT custom_title FROM conversation_metadata WHERE conversation_id = :conversationId")
    suspend fun getCustomTitle(conversationId: Long): String?

    /**
     * Exclui os metadados de uma conversa específica.
     * Importante chamar ao excluir a conversa principal.
     */
    @Query("DELETE FROM conversation_metadata WHERE conversation_id = :conversationId")
    suspend fun deleteMetadata(conversationId: Long)

    /**
     * Retorna um Flow com a lista completa de metadados.
     * Necessário para que o ViewModel observe mudanças nos títulos personalizados.
     */
    @Query("SELECT * FROM conversation_metadata")
    fun getAllMetadata(): Flow<List<ConversationMetadataEntity>>
}