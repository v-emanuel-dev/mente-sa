package com.example.mentesa.data.db // Use o mesmo pacote da Entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow // Import necessário para Flow

// Import da sua Entidade (ajuste o pacote se necessário)
import com.example.mentesa.data.db.ChatMessageEntity

/**
 * DAO (Data Access Object) para interagir com a tabela 'chat_messages'.
 * Define os métodos para acessar o banco de dados.
 */
@Dao // Marca a interface como um DAO do Room
interface ChatDao {

    /**
     * Insere uma nova mensagem na tabela.
     * Se conflitar, substitui a antiga (útil se houvesse updates).
     * Deve ser chamada de uma coroutine.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    /**
     * Busca todas as mensagens, ordenadas por timestamp crescente.
     * Retorna um Flow para observação reativa de mudanças no banco de dados.
     */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    /**
     * (Opcional) Apaga todas as mensagens da tabela.
     * Deve ser chamada de uma coroutine.
     */
    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    // Poderíamos adicionar queries mais específicas aqui no futuro,
    // como buscar por ID, buscar por data, etc.
}