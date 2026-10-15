package com.freeup.textactions.history

import com.freeup.textactions.data.local.db.ActionHistoryDao
import com.freeup.textactions.data.mapper.toDomain
import com.freeup.textactions.data.mapper.toEntity
import com.freeup.textactions.domain.model.ActionHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepository(private val dao: ActionHistoryDao) {

    fun getRecentActions(limit: Int = 50): Flow<List<ActionHistory>> {
        return dao.getRecentActions(limit).map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun insert(action: ActionHistory) {
        dao.insert(action.toEntity())
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }
}
