package com.example.speakandroid

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class SpeechRecorderManager(private val context: Context) {

    data class LiveState(
        val isRecording: Boolean = false,
        val elapsedMillis: Long = 0,
        val transcriptEnglish: String = "",
        val lastError: String? = null
    )

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state

    private var mediaRecorder: MediaRecorder? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var outputFile: File? = null
    private var startElapsedRealtime = 0L

    private var finalTranscript = ""
    private var partialTranscript = ""

    fun start(): String? {
        if (_state.value.isRecording) return null
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return "Speech recognition service is not available on this device."
        }

        outputFile = File(context.filesDir, "rec_${System.currentTimeMillis()}.m4a")
        finalTranscript = ""
        partialTranscript = ""

        mediaRecorder = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        }.getOrNull()?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(96000)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
            start()
        } ?: return "Unable to start microphone recording."

        speechRecognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }
            .getOrNull()
            ?.apply {
                setRecognitionListener(recognitionListener)
                startListening(recognizerIntent())
            } ?: run {
            runCatching { mediaRecorder?.stop() }
            runCatching { mediaRecorder?.release() }
            mediaRecorder = null
            return "Unable to start speech recognizer."
        }

        startElapsedRealtime = SystemClock.elapsedRealtime()
        _state.value = LiveState(isRecording = true, transcriptEnglish = "", lastError = null)
        return null
    }

    fun tick() {
        if (!_state.value.isRecording) return
        _state.value = _state.value.copy(
            elapsedMillis = SystemClock.elapsedRealtime() - startElapsedRealtime
        )
    }

    fun stop(): Triple<File?, String, Long> {
        val transcript = combineText()
        val duration = if (startElapsedRealtime == 0L) 0L else SystemClock.elapsedRealtime() - startElapsedRealtime
        runCatching { speechRecognizer?.stopListening() }
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null

        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null

        _state.value = LiveState()
        startElapsedRealtime = 0L
        return Triple(outputFile, transcript, duration)
    }

    fun play(filePath: String, onCompleted: () -> Unit) {
        val player = MediaPlayer()
        player.setDataSource(filePath)
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener {
            it.release()
            onCompleted()
        }
        player.prepareAsync()
    }

    private fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun combineText(): String = listOf(finalTranscript, partialTranscript)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()

    private fun errorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognizer client error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing."
        SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found. Try speaking clearly."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Retrying..."
        SpeechRecognizer.ERROR_SERVER -> "Speech recognizer server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
        else -> "Speech recognizer error: $error"
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            _state.value = _state.value.copy(lastError = errorText(error))
            if (_state.value.isRecording) {
                runCatching { speechRecognizer?.startListening(recognizerIntent()) }
            }
        }

        override fun onResults(results: android.os.Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            if (text.isNotBlank()) {
                finalTranscript = (finalTranscript + " " + text).trim()
                partialTranscript = ""
                _state.value = _state.value.copy(transcriptEnglish = finalTranscript, lastError = null)
            }

            if (_state.value.isRecording) {
                runCatching { speechRecognizer?.startListening(recognizerIntent()) }
            }
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            partialTranscript = text
            _state.value = _state.value.copy(transcriptEnglish = combineText(), lastError = null)
        }

        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
    }
}
