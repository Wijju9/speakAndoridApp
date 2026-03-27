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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
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
                SpeakApp(
                    state = uiState,
                    onStart = viewModel::startRecording,
                    onStop = viewModel::stopRecording,
                    onPlay = viewModel::playRecording,
                    onDelete = viewModel::deleteRecording
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

private enum class AppScreen { RECORD, HISTORY }

@Composable
fun SpeakApp(
    state: MainViewModel.UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPlay: (RecordingEntity) -> Unit,
    onDelete: (RecordingEntity) -> Unit
) {
    var screen by remember { mutableStateOf(AppScreen.RECORD) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Speak Android", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (screen == AppScreen.RECORD) {
                Button(onClick = { screen = AppScreen.HISTORY }) { Text("Recordings List") }
            } else {
                Button(onClick = { screen = AppScreen.RECORD }) { Text("Back") }
            }
        }

        when (screen) {
            AppScreen.RECORD -> RecordScreen(
                state = state,
                onStart = onStart,
                onStop = onStop
            )
            AppScreen.HISTORY -> HistoryScreen(
                recordings = state.recordings,
                isPlayingId = state.isPlayingId,
                onPlay = onPlay,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun RecordScreen(
    state: MainViewModel.UiState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val hours = state.elapsedMillis / 3_600_000
    val minutes = (state.elapsedMillis % 3_600_000) / 60_000
    val seconds = (state.elapsedMillis % 60_000) / 1000

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Text("English", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tap mic and speak", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(
                    text = if (state.liveEnglishText.isBlank()) "Speech text will appear here..." else state.liveEnglishText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 28.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Timer: %02d:%02d:%02d".format(hours, minutes, seconds))
        Text("Status: ${state.status}", color = if (state.status.contains("error", true)) Color.Red else Color.Gray)
        Text("Debug log file: files/stt_logs.txt", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onStart, enabled = !state.isRecording) { Text("🎤 Start") }
            Button(onClick = onStop, enabled = state.isRecording) { Text("■ Stop") }
        }
    }
}

@Composable
private fun HistoryScreen(
    recordings: List<RecordingEntity>,
    isPlayingId: Long?,
    onPlay: (RecordingEntity) -> Unit,
    onDelete: (RecordingEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Saved recordings: ${recordings.size}", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recordings, key = { it.id }) { row ->
                RecordingCard(
                    row = row,
                    isPlaying = isPlayingId == row.id,
                    hasAudio = row.filePath.isNotBlank(),
                    onPlay = { onPlay(row) },
                    onDelete = { onDelete(row) }
                )
            }
        }
    }
}

@Composable
private fun RecordingCard(
    row: RecordingEntity,
    isPlaying: Boolean,
    hasAudio: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Recorded: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(row.createdAt))}"
            )
            Text("Duration: ${row.durationMillis / 1000}s")
            Text("English: ${row.englishText}")
            if (!hasAudio) {
                Text("Audio: Not saved (text-only mode)")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay, enabled = hasAudio && !isPlaying) {
                    Text(if (isPlaying) "Playing..." else "Replay")
                }
                Button(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}
