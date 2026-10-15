package com.freeup.textactions.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val content: String,
    val createdAt: Long,
    val category: String = "General",
    val isLocked: Boolean = false,
    val sourceAppName: String? = null,
    val sourcePackageName: String? = null,
    val sourceUrl: String? = null,
    val detectedType: String = "TEXT", // TEXT, PHONE, URL, EMAIL, ADDRESS
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val pinnedAt: Long? = null
)
