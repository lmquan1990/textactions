package com.freeup.textactions.domain

import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.tasks.await

class SummarizeTextUseCase {
    private val entityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
    )

    suspend fun execute(text: String): SummaryResult {
        return try {
            val cleaned = text.trim()
            if (cleaned.length < 30) {
                return SummaryResult.Error("Text too short")
            }
            if (cleaned.length > 2000) {
                return SummaryResult.Error("Text too long")
            }

            val sentences = splitSentences(cleaned)
            if (sentences.isEmpty()) return SummaryResult.Error("No sentences found")

            val entityMentions = extractEntityMentions(cleaned)
            val wordFreq = buildWordFrequency(cleaned)

            val scored = sentences.mapIndexed { index, sentence ->
                val score = scoreSentence(sentence, index, sentences.size, wordFreq, entityMentions)
                ScoredSentence(index, sentence, score)
            }

            val takeCount = if (sentences.size > 3) 2 else 1

            val top = scored
                .sortedWith(compareByDescending<ScoredSentence> { it.score }.thenBy { it.index })
                .take(takeCount)
                .sortedBy { it.index }

            val summary = if (top.isEmpty()) {
                sentences.first().trim()
            } else {
                top.joinToString("\n") { it.sentence.trim() }
            }
            SummaryResult.Success(summary)
        } catch (e: Exception) {
            SummaryResult.Error(e.message ?: "Summarization failed")
        }
    }

    private suspend fun extractEntityMentions(text: String): List<EntityMention> {
        return try {
            entityExtractor.downloadModelIfNeeded().await()
            val annotations = entityExtractor.annotate(text).await()
            val importantTypes = resolveImportantEntityTypes()
            val mentions = linkedMapOf<String, MutableSet<Int>>()
            for (annotation in annotations) {
                val mentionText = annotation.annotatedText.trim()
                if (mentionText.isBlank()) continue
                val types = annotation.entities
                    .mapNotNull { entity -> entity.type.takeIf { it in importantTypes } }
                    .toSet()
                if (types.isNotEmpty()) {
                    val existing = mentions.getOrPut(mentionText) { mutableSetOf() }
                    existing.addAll(types)
                }
            }
            mentions.map { EntityMention(it.key, it.value) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildWordFrequency(text: String): Map<String, Int> {
        val freq = mutableMapOf<String, Int>()
        tokenizeForKeywords(text).forEach { word ->
            freq[word] = (freq[word] ?: 0) + 1
        }
        return freq
    }

    private fun scoreSentence(
        sentence: String,
        index: Int,
        total: Int,
        wordFreq: Map<String, Int>,
        entityMentions: List<EntityMention>
    ): Double {
        val wordCount = tokenizeAllWords(sentence).size
        val lengthScore = when {
            wordCount in 8..25 -> 3.0
            wordCount in 6..7 || wordCount in 26..35 -> 1.0
            else -> -2.0
        }

        val sentenceKeywords = tokenizeForKeywords(sentence).toSet()
        val keywordScore = sentenceKeywords
            .count { keyword -> (wordFreq[keyword] ?: 0) >= 2 }
            .coerceAtMost(5)
            .toDouble()

        val entityTypes = mutableSetOf<Int>()
        for (mention in entityMentions) {
            if (sentence.contains(mention.text, ignoreCase = true)) {
                entityTypes.addAll(mention.types)
            }
        }
        val entityScore = entityTypes.size.toDouble()

        val positionScore = when {
            index == 0 && index == total - 1 -> 6.0
            index == 0 -> 4.0
            index == total - 1 -> 2.0
            else -> 0.0
        }

        return lengthScore + keywordScore + entityScore + positionScore
    }

    private fun tokenizeAllWords(text: String): List<String> {
        return wordRegex.findAll(text.lowercase())
            .map { it.value }
            .toList()
    }

    private fun tokenizeForKeywords(text: String): List<String> {
        return wordRegex.findAll(text.lowercase())
            .map { it.value }
            .filter { it !in stopWords }
            .toList()
    }

    private data class EntityMention(val text: String, val types: Set<Int>)

    private companion object {
        private val wordRegex = Regex("[\\p{L}\\p{N}]+")
        private val stopWords = setOf(
            "the", "is", "are", "was", "were", "am", "be", "been", "being",
            "a", "an", "and", "or", "but", "if", "then", "than", "to", "in", "on",
            "for", "of", "with", "at", "by", "from", "as", "it", "this", "that",
            "these", "those", "you", "your", "we", "our", "they", "their", "he",
            "she", "his", "her", "i", "me", "my", "mine", "us", "them",
            "va", "la", "cua", "nhung", "nhu", "voi", "mot", "nhieu", "duoc",
            "dang", "da", "se", "cho", "neu", "thi", "trong", "tren", "duoi",
            "ve", "den", "tu", "boi", "toi", "ban", "chung", "cac", "khong",
            "co",
            "và", "là", "của", "những", "như", "với", "một", "nhiều", "được",
            "đang", "đã", "sẽ", "cho", "nếu", "thì", "trong", "trên", "dưới",
            "về", "đến", "từ", "bởi", "tôi", "bạn", "chúng", "các", "không", "có"
        )
    }

    private data class ScoredSentence(val index: Int, val sentence: String, val score: Double)
}

private fun resolveImportantEntityTypes(): Set<Int> {
    val typeNames = listOf(
        "TYPE_ORGANIZATION",
        "TYPE_LOCATION",
        "TYPE_DATE_TIME"
    )
    val types = mutableSetOf<Int>()
    for (name in typeNames) {
        try {
            val field = Entity::class.java.getField(name)
            types.add(field.getInt(null))
        } catch (_: Exception) {
            // Ignore missing fields for current ML Kit version
        }
    }
    return types
}

fun splitSentences(text: String): List<String> {
    val sentences = mutableListOf<String>()
    val builder = StringBuilder()
    for (char in text) {
        builder.append(char)
        if (char == '.' || char == '!' || char == '?') {
            val sentence = builder.toString().trim()
            if (sentence.isNotBlank()) {
                sentences.add(sentence)
            }
            builder.clear()
        }
    }
    val remaining = builder.toString().trim()
    if (remaining.isNotBlank()) {
        sentences.add(remaining)
    }
    return sentences
}

sealed class SummaryResult {
    data class Success(val text: String) : SummaryResult()
    data class Error(val message: String) : SummaryResult()
}
