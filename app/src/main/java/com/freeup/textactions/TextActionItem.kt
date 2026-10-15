package com.freeup.textactions

/**
 * Represent an actionable item generated from text analysis.
 */
data class TextActionItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val icon: Any, // Can be Int (Resource ID) or ImageVector
    val action: () -> Unit
)
