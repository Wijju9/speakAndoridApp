package com.example.speakandroid

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val isRecording: Boolean = false,
        val elapsedMillis: Long = 0,
        val liveEnglishText: String = "",
        val recordings: List<RecordingEntity> = emptyList(),
        val isPlayingId: Long? = null,
        val status: String = "Ready"
    )

    private val dao = AppDatabase.get(application).recordingDao()
    private val recorderManager = SpeechRecorderManager(application)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { rows ->
                _uiState.update { it.copy(recordings = rows) }
            }
        }

        viewModelScope.launch {
            recorderManager.state.collect { live ->
                _uiState.update {
                    it.copy(
                        isRecording = live.isRecording,
                        elapsedMillis = live.elapsedMillis,
                        liveEnglishText = live.transcriptEnglish,
                        status = live.lastError ?: it.status
                    )
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                delay(1000)
                recorderManager.tick()
            }
        }
    }

    fun startRecording() {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            _uiState.update { it.copy(status = "Microphone permission is required. Please allow RECORD_AUDIO.") }
            return
        }

        val startError = recorderManager.start()
        if (startError != null) {
            _uiState.update { it.copy(status = "$startError Check log: files/stt_logs.txt") }
            return
        }
        _uiState.update {
            it.copy(
                status = "Recording started. Live English transcription running..."
            )
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            _uiState.update { it.copy(status = "Stopping and saving English text...") }
            val (english, durationMillis) = recorderManager.stop()

            dao.insert(
                RecordingEntity(
                    filePath = "",
                    createdAt = System.currentTimeMillis(),
                    durationMillis = durationMillis,
                    englishText = english,
                    hindiText = "",
                    gujaratiText = ""
                )
            )
            _uiState.update {
                it.copy(
                    status = if (english.isBlank()) {
                        "No English text recognized. Check log: files/stt_logs.txt"
                    } else {
                        "Text converted successfully."
                    }
                )
            }
        }
    }

    fun deleteRecording(row: RecordingEntity) {
        viewModelScope.launch {
            dao.deleteById(row.id)
            runCatching {
                val f = java.io.File(row.filePath)
                if (f.exists()) f.delete()
            }
            _uiState.update { it.copy(status = "Recording deleted.") }
        }
    }

    fun playRecording(row: RecordingEntity) {
        if (row.filePath.isBlank()) {
            _uiState.update { it.copy(status = "This entry has text only. Audio replay is not available.") }
            return
        }
        _uiState.update { it.copy(isPlayingId = row.id, status = "Playing audio...") }
        recorderManager.play(row.filePath) {
            _uiState.update { it.copy(isPlayingId = null, status = "Playback complete.") }
        }
    }
}
