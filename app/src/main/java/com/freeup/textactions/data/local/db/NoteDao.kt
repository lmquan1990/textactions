package com.freeup.textactions.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT COUNT(*) FROM notes WHERE content = :content AND createdAt >= :since")
    suspend fun countByContentSince(content: String, since: Long): Int

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND isLocked = 0 ORDER BY isPinned DESC, pinnedAt DESC, createdAt DESC")
    fun getPublicSnippets(): kotlinx.coroutines.flow.Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND isLocked = 0 ORDER BY isPinned DESC, pinnedAt DESC, createdAt DESC")
    suspend fun getPublicSnippetsList(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND isLocked = 1 ORDER BY isPinned DESC, pinnedAt DESC, createdAt DESC")
    fun getLockedSnippets(): kotlinx.coroutines.flow.Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 ORDER BY isPinned DESC, pinnedAt DESC, createdAt DESC")
    fun getArchivedNotes(): kotlinx.coroutines.flow.Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
