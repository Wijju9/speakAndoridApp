package com.example.speakandroid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    private val translatorRepository = TranslatorRepository()

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
                        liveEnglishText = live.transcriptEnglish
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
        recorderManager.start()
        _uiState.update {
            it.copy(
                status = "Recording started. Live English transcription running..."
            )
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            _uiState.update { it.copy(status = "Stopping and translating...") }
            val (file, english, durationMillis) = recorderManager.stop()
            if (file == null) {
                _uiState.update { it.copy(status = "No recording file created.") }
                return@launch
            }

            val (hindi, gujarati) = translatorRepository.toHindiAndGujarati(english)
            dao.insert(
                RecordingEntity(
                    filePath = file.absolutePath,
                    createdAt = System.currentTimeMillis(),
                    durationMillis = durationMillis,
                    englishText = english,
                    hindiText = hindi,
                    gujaratiText = gujarati
                )
            )
            _uiState.update {
                it.copy(
                    status = "Saved locally. English + Hindi + Gujarati text ready."
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
        _uiState.update { it.copy(isPlayingId = row.id, status = "Playing audio...") }
        recorderManager.play(row.filePath) {
            _uiState.update { it.copy(isPlayingId = null, status = "Playback complete.") }
        }
    }
}
