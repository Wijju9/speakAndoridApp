package com.example.speakandroid

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "SpeakAndroid"
    private const val LOG_FILE_NAME = "stt_logs.txt"

    fun info(context: Context, message: String) = write(context, "INFO", message)
    fun error(context: Context, message: String) = write(context, "ERROR", message)

    private fun write(context: Context, level: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = "$timestamp [$level] $message"
        if (level == "ERROR") Log.e(TAG, message) else Log.i(TAG, message)
        runCatching {
            val file = java.io.File(context.filesDir, LOG_FILE_NAME)
            file.appendText(line + "\n")
        }
    }
}
