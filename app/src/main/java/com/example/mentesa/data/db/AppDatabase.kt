package com.example.mentesa.data.db // Use o mesmo pacote da Entity e DAO

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Import da Entidade e DAO (ajuste o pacote se necessário)
import com.example.mentesa.data.db.ChatMessageEntity
import com.example.mentesa.data.db.ChatDao

/**
 * Classe principal do banco de dados Room para o aplicativo MenteSã.
 * Define as entidades (tabelas) e fornece acesso aos DAOs.
 * Implementa o padrão Singleton para garantir uma única instância.
 */
@Database(
    entities = [ChatMessageEntity::class], // Lista as tabelas do banco
    version = 1,                          // Versão inicial do schema
    exportSchema = false                  // Simplifica, não exporta schema para versionamento
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Método abstrato que o Room implementará para fornecer o DAO.
     */
    abstract fun chatDao(): ChatDao

    /**
     * Companion object para o padrão Singleton.
     */
    companion object {
        // @Volatile garante que o valor de INSTANCE seja sempre atualizado e visível
        // para todas as threads imediatamente.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Obtém a instância única (Singleton) do banco de dados.
         * Cria o BD na primeira chamada.
         */
        fun getDatabase(context: Context): AppDatabase {
            // Retorna a instância se já existir
            return INSTANCE ?: synchronized(this) {
                // Cria a instância dentro de um bloco sincronizado se for nula
                val instance = Room.databaseBuilder(
                    context.applicationContext, // Contexto da aplicação
                    AppDatabase::class.java,    // Sua classe AppDatabase
                    "mentesa_database"          // Nome do arquivo do banco de dados
                )
                    // Poderia adicionar .fallbackToDestructiveMigration() aqui durante o desenvolvimento
                    // para evitar a necessidade de criar migrações a cada mudança no schema.
                    .build()
                INSTANCE = instance // Armazena a instância criada
                // Retorna a instância
                instance
            }
        }
    }
}