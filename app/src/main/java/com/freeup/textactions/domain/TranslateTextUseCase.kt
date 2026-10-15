package com.freeup.textactions.domain

import com.google.mlkit.nl.translate.*
import kotlinx.coroutines.tasks.await
import java.util.Locale

class TranslateTextUseCase {

    companion object {
        private const val MAX_CHUNK_SIZE = 4500 // ML Kit works best under 5000 chars
    }

    suspend fun execute(text: String, targetLanguage: String? = null, knownSourceLanguage: String? = null, allowDownload: Boolean = true): TranslationResult {
        return try {
            // Clean text: remove problematic characters that ML Kit can't handle
            val cleanedText = text.replace('\ufffc', ' ') // Remove object replacement char
                .replace(Regex("\\s+"), " ") // Normalize whitespace
                .trim()

            android.util.Log.d("TranslateTextUseCase", "Original text length: ${text.length}, Cleaned: ${cleanedText.length}")

            val sourceLangCode = if (knownSourceLanguage != null) {
                knownSourceLanguage
            } else {
                val languageIdentifier = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
                languageIdentifier.identifyLanguage(cleanedText).await()
            }

            if (sourceLangCode == "und") {
                return TranslationResult.Error("Could not detect language")
            }

            val targetLangCode = targetLanguage ?: Locale.getDefault().language

            val sourceLang = TranslateLanguage.fromLanguageTag(sourceLangCode) ?: return TranslationResult.Error("Unsupported source language")
            val targetLang = TranslateLanguage.fromLanguageTag(targetLangCode) ?: TranslateLanguage.VIETNAMESE

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()

            if (!allowDownload) {
                // ML Kit pivots through English, so a pair without English needs three models
                val required = buildList {
                    add(sourceLang); add(targetLang)
                    if (sourceLang != TranslateLanguage.ENGLISH && targetLang != TranslateLanguage.ENGLISH) {
                        add(TranslateLanguage.ENGLISH)
                    }
                }.distinct()
                val downloaded = com.google.mlkit.common.model.RemoteModelManager.getInstance()
                    .getDownloadedModels(com.google.mlkit.nl.translate.TranslateRemoteModel::class.java)
                    .await().map { it.language }.toSet()
                val missing = required.filter { it !in downloaded }
                if (missing.isNotEmpty()) {
                    return TranslationResult.ModelsMissing(sourceLangCode, targetLangCode, missing)
                }
            }

            val translator = Translation.getClient(options)
            translator.downloadModelIfNeeded().await()

            // Handle long texts by chunking
            val translatedText = if (cleanedText.length <= MAX_CHUNK_SIZE) {
                android.util.Log.d("TranslateTextUseCase", "Translating single chunk: ${cleanedText.length} chars")
                translator.translate(cleanedText).await()
            } else {
                android.util.Log.d("TranslateTextUseCase", "Translating in chunks: ${cleanedText.length} chars")
                translateLongText(cleanedText, translator)
            }

            android.util.Log.d("TranslateTextUseCase", "Translation result length: ${translatedText.length}")
            TranslationResult.Success(translatedText, sourceLangCode, targetLangCode)
        } catch (e: Exception) {
            android.util.Log.e("TranslateTextUseCase", "Translation error: ${e.message}", e)
            TranslationResult.Error(e.message ?: "Translation failed")
        }
    }

    private suspend fun translateLongText(text: String, translator: Translator): String {
        val chunks = splitIntoChunks(text, MAX_CHUNK_SIZE)
        val translatedChunks = chunks.map { chunk ->
            translator.translate(chunk).await()
        }
        return translatedChunks.joinToString("")
    }

    private fun splitIntoChunks(text: String, maxSize: Int): List<String> {
        if (text.length <= maxSize) return listOf(text)

        val chunks = mutableListOf<String>()
        var currentIndex = 0

        while (currentIndex < text.length) {
            val endIndex = minOf(currentIndex + maxSize, text.length)
            var splitIndex = endIndex

            // Try to split at sentence boundary if not at the end
            if (endIndex < text.length) {
                // Look for sentence endings (., !, ?, newline) within the last 500 chars
                val searchStart = maxOf(currentIndex, endIndex - 500)
                val chunk = text.substring(searchStart, endIndex)
                val sentenceEnd = chunk.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))

                if (sentenceEnd != -1) {
                    splitIndex = searchStart + sentenceEnd + 1
                }
            }

            chunks.add(text.substring(currentIndex, splitIndex))
            currentIndex = splitIndex
        }

        return chunks
    }
}

sealed class TranslationResult {
    data class Success(val text: String, val sourceLang: String, val targetLang: String) : TranslationResult()
    data class Error(val message: String) : TranslationResult()
    /** Required offline models aren't on the device; the user must choose to download them. */
    data class ModelsMissing(
        val sourceLang: String,
        val targetLang: String,
        val missingLangs: List<String>
    ) : TranslationResult()
}
