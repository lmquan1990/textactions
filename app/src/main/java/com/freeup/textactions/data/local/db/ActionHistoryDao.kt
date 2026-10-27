package com.freeup.textactions.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionHistoryDao {
    @Query("SELECT * FROM action_history ORDER BY isPinned DESC, pinnedAt DESC, createdAt DESC LIMIT :limit")
    fun getRecentActions(limit: Int = 3): Flow<List<ActionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: ActionHistoryEntity): Long

    @Query("SELECT COUNT(*) FROM action_history WHERE actionType = :actionType AND inputText = :inputText AND createdAt >= :since")
    suspend fun countRecent(actionType: String, inputText: String, since: Long): Int

    @Query("UPDATE action_history SET isPinned = :pinned, pinnedAt = :pinnedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, pinnedAt: Long?)

    @Query("DELETE FROM action_history WHERE actionType IN ('SAVE_NOTE', 'SAVE_TO_NOTE') AND (resultText = :noteId OR inputText = :content OR inputText = :titleAndContent)")
    suspend fun deleteNoteHistory(noteId: String, content: String, titleAndContent: String)

    @Query("DELETE FROM action_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM action_history ORDER BY isPinned DESC, pinnedAt DESC, createdAt DESC")
    fun getAllActions(): Flow<List<ActionHistoryEntity>>

    @Query("DELETE FROM action_history")
    suspend fun clearAll()
}
