package com.example.speakandroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensurePermissions()
        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsState()
                RecordingScreen(
                    state = uiState,
                    onStart = viewModel::startRecording,
                    onStop = viewModel::stopRecording,
                    onPlay = viewModel::playRecording
                )
            }
        }
    }

    private fun ensurePermissions() {
        val required = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
fun RecordingScreen(
    state: MainViewModel.UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPlay: (RecordingEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Realtime Voice Recorder + Translation", style = MaterialTheme.typography.titleLarge)
        Text("Status: ${state.status}")

        val hours = state.elapsedMillis / 3_600_000
        val minutes = (state.elapsedMillis % 3_600_000) / 60_000
        val seconds = (state.elapsedMillis % 60_000) / 1000

        Text("Recording timer: %02d:%02d:%02d".format(hours, minutes, seconds))
        Text("Long-session ready: keep app active for 2+ hours recordings.")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = !state.isRecording) { Text("Start Recording") }
            Button(onClick = onStop, enabled = state.isRecording) { Text("Stop Recording") }
        }

        Text("Live English text:")
        Text(
            if (state.liveEnglishText.isBlank()) "(Speak now - transcript appears instantly)"
            else state.liveEnglishText
        )

        Text("Saved sessions (${state.recordings.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.recordings, key = { it.id }) { row ->
                RecordingCard(
                    row = row,
                    isPlaying = state.isPlayingId == row.id,
                    onPlay = { onPlay(row) }
                )
            }
        }
    }
}

@Composable
private fun RecordingCard(
    row: RecordingEntity,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Recorded: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(row.createdAt))}"
            )
            Text("Duration: ${row.durationMillis / 1000}s")
            Text("English: ${row.englishText}")
            Text("Hindi: ${row.hindiText}")
            Text("Gujarati: ${row.gujaratiText}")
            Button(onClick = onPlay, enabled = !isPlaying) {
                Text(if (isPlaying) "Playing..." else "Replay")
            }
        }
    }
}
