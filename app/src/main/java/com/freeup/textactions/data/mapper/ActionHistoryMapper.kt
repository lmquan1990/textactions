package com.freeup.textactions.data.mapper

import com.freeup.textactions.data.local.db.ActionHistoryEntity
import com.freeup.textactions.domain.model.ActionHistory

fun ActionHistoryEntity.toDomain(): ActionHistory {
    return ActionHistory(
        id = id,
        actionType = actionType,
        inputText = inputText,
        resultText = resultText,
        metadata = metadata,
        createdAt = createdAt,
        isPinned = isPinned,
        pinnedAt = pinnedAt
    )
}

fun ActionHistory.toEntity(): ActionHistoryEntity {
    return ActionHistoryEntity(
        id = id,
        actionType = actionType,
        inputText = inputText,
        resultText = resultText,
        metadata = metadata,
        createdAt = createdAt,
        isPinned = isPinned,
        pinnedAt = pinnedAt
    )
}
