package com.example.mentesa.data.db

import androidx.room.ColumnInfo

data class ConversationInfo(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "lastTimestamp") val lastTimestamp: Long
)