package com.example.mentesa.data.db // Use o mesmo pacote da Entity e DAO

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
// Import da Entidade e DAO (ajuste o pacote se necessário)
// Removidos imports duplicados, mantenha apenas os necessários
// import com.example.mentesa.data.db.ChatMessageEntity // Já importado implicitamente pelo @Database
// import com.example.mentesa.data.db.ChatDao // Já importado pelo retorno de chatDao()

/**
 * Classe principal do banco de dados Room para o aplicativo MenteSã.
 * Define as entidades (tabelas) e fornece acesso aos DAOs.
 * Implementa o padrão Singleton para garantir uma única instância.
 */
@Database(
    entities = [ChatMessageEntity::class], // Lista as tabelas do banco
    version = 2,                          // <<---- INCREMENTE A VERSÃO AQUI (ex: de 1 para 2)
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
                    // --- ADICIONE ISTO DURANTE O DESENVOLVIMENTO ---
                    // Se a versão aumentar e não houver migration,
                    // o Room destruirá o DB antigo e criará um novo.
                    // ATENÇÃO: APAGA TODOS OS DADOS EXISTENTES!
                    .fallbackToDestructiveMigration()
                    // --- FIM DA ADIÇÃO ---
                    .build()
                INSTANCE = instance // Armazena a instância criada
                // Retorna a instância
                instance
            }
        }
    }
}