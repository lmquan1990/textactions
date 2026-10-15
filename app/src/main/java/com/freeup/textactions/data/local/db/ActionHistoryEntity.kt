package com.freeup.textactions.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_history")
data class ActionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val inputText: String,
    val resultText: String? = null,
    val metadata: String? = null, // JSON string for extra info
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val pinnedAt: Long? = null
)
