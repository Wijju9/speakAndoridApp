package com.example.speakandroid

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranslatorRepository {

    suspend fun toHindiAndGujarati(englishText: String): Pair<String, String> = withContext(Dispatchers.IO) {
        if (englishText.isBlank()) return@withContext "" to ""

        val hindi = translate(englishText, TranslateLanguage.HINDI)
        val gujarati = translate(englishText, TranslateLanguage.GUJARATI)
        hindi to gujarati
    }

    private fun translate(input: String, targetLanguage: String): String {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLanguage)
            .build()

        val translator = Translation.getClient(options)
        return try {
            Tasks.await(translator.downloadModelIfNeeded())
            Tasks.await(translator.translate(input))
        } catch (_: Exception) {
            "Translation unavailable"
        } finally {
            translator.close()
        }
    }
}
