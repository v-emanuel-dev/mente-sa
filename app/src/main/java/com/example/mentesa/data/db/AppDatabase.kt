package com.example.mentesa.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
// Import das Entidades e DAOs
// import com.example.mentesa.data.db.ChatMessageEntity // Implícito
// import com.example.mentesa.data.db.ChatDao // Implícito
// import com.example.mentesa.data.db.ConversationMetadataEntity // NOVO
// import com.example.mentesa.data.db.ConversationMetadataDao // NOVO

/**
 * Classe principal do banco de dados Room para o aplicativo MenteSã.
 */
@Database(
    // Adiciona a nova entidade à lista
    entities = [ChatMessageEntity::class, ConversationMetadataEntity::class], // <<-- ADICIONADO ConversationMetadataEntity
    // INCREMENTE a versão para o próximo número (se estava em 2, vai para 3, etc.)
    version = 3,                          // <<-- INCREMENTE A VERSÃO AQUI
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    // Adiciona o novo DAO
    abstract fun conversationMetadataDao(): ConversationMetadataDao // <<-- ADICIONADO

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mentesa_database"
                )
                    // Mantenha .fallbackToDestructiveMigration() durante o desenvolvimento.
                    // LEMBRE-SE: Isso apagará todos os dados existentes (mensagens E títulos)
                    // na próxima vez que o app rodar após o aumento da versão!
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}