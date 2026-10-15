package com.freeup.textactions.backup

data class BackupPayload(
    val version: Int,
    val exportedAt: Long,
    val notes: List<BackupNote>
)

data class BackupNote(
    val id: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val locked: Boolean,
    val archived: Boolean,
    val pinned: Boolean,
    val sourceApp: String?,
    val sourceUrl: String?
)
