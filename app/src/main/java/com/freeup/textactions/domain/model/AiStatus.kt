package com.freeup.textactions.domain.model

enum class AiState {
    READY,
    INITIALIZING,
    DOWNLOADING,
    ERROR
}

data class AiStatus(
    val state: AiState,
    val progress: Int = 0
)
