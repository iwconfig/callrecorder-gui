package com.android.bcrgui.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.bcrgui.model.Bookmark
import com.android.bcrgui.model.AiTranscription
import com.android.bcrgui.model.AiMetadata
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val recording by viewModel.selectedRecording.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val bookmarks by viewModel.selectedBookmarks.collectAsState()
    val transcript by viewModel.selectedTranscript.collectAsState()
    val metadata by viewModel.selectedMetadata.collectAsState()

    android.util.Log.d("PlayerSheet", "State: recording=${recording?.displayName}, transcript=${transcript?.text?.take(50)}, metadata=${metadata?.summary?.take(50)}")

    var isExpanded by remember { mutableStateOf(false) }
    var showTranscript by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkLabel by remember { mutableStateOf("") }
    val context = LocalContext.current

    if (isExpanded) {
        BackHandler {
            isExpanded = false
        }
    }

    if (recording == null) return

    val rec = recording!!

    AnimatedContent(
        targetState = isExpanded,
        modifier = modifier,
        transitionSpec = {
            slideInVertically(initialOffsetY = { it }) + fadeIn() togetherWith
                    slideOutVertically(targetOffsetY = { it }) + fadeOut()
        },
        label = "PlayerAnimation"
    ) { expanded ->
        if (expanded) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isExpanded = false }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize")
                        }
                        Text(
                            text = "Now Playing",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, rec.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Recording"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.2f))

                    val dirColor = when (rec.direction?.lowercase()) {
                        "in" -> Color(0xFF4CAF50)
                        "out" -> Color(0xFF6200EE)
                        else -> Color(0xFFFF9800)
                    }
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(dirColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (rec.direction?.lowercase()) {
                                "in" -> Icons.Default.CallReceived
                                "out" -> Icons.Default.CallMade
                                else -> Icons.Default.Call
                            },
                            contentDescription = null,
                            tint = dirColor,
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = rec.resolvedName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    rec.resolvedSubtext?.let { sub ->
                        Text(
                            text = sub,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Text(
                        text = rec.date ?: "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.weight(0.3f))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..(if (duration > 0) duration.toFloat() else 100f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(currentPosition),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDuration(duration),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            val isSelected = playbackSpeed == speed
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable { viewModel.setPlaybackSpeed(speed) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${speed}x",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.skipBackward() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Back 10s",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        FloatingActionButton(
                            onClick = { viewModel.togglePlayPause() },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.skipForward() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showBookmarks = true }) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Bookmarks",
                                tint = if (bookmarks.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(onClick = {
                            android.util.Log.d("PlayerSheet", "Transcript toggle: showTranscript=$showTranscript, transcript=${transcript?.text?.take(50)}")
                            showTranscript = !showTranscript
                        }) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Transcript",
                                tint = if (showTranscript) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (showTranscript) {
                        val currentTranscript = transcript
                        android.util.Log.d("PlayerSheet", "Rendering transcript card: currentTranscript=${currentTranscript?.text?.take(50)}")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Transcript",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (currentTranscript != null) {
                                    Text(
                                        text = currentTranscript.text,
                                        fontSize = 12.sp,
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    )
                                } else {
                                    Text(
                                        text = "No transcript available",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    val currentMetadata = metadata
                    android.util.Log.d("PlayerSheet", "Rendering metadata card: currentMetadata=${currentMetadata?.summary?.take(50)}")
                    if (currentMetadata != null && currentMetadata.summary != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "AI Summary",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = currentMetadata.summary ?: "",
                                    fontSize = 12.sp
                                )
                                if (!currentMetadata.tags.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        currentMetadata.tags.forEach { tag ->
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = tag,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.2f))
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { isExpanded = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column {
                    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dirColor = when (rec.direction?.lowercase()) {
                            "in" -> Color(0xFF4CAF50)
                            "out" -> Color(0xFF6200EE)
                            else -> Color(0xFFFF9800)
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(dirColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (rec.direction?.lowercase()) {
                                    "in" -> Icons.Default.CallReceived
                                    "out" -> Icons.Default.CallMade
                                    else -> Icons.Default.Call
                                },
                                contentDescription = null,
                                tint = dirColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rec.resolvedName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatDuration(currentPosition)} / ${formatDuration(duration)} • ${playbackSpeed}x",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { viewModel.togglePlayPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause"
                            )
                        }

                        IconButton(onClick = { viewModel.selectRecording(null) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Player"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBookmarks && rec != null) {
        BookmarksBottomSheet(
            bookmarks = bookmarks,
            duration = duration,
            onDismiss = { showBookmarks = false },
            onSeek = { timestampMs ->
                viewModel.seekTo(timestampMs)
                showBookmarks = false
            },
            onAdd = {
                showAddBookmarkDialog = true
            },
            onDelete = { timestampMs ->
                viewModel.deleteBookmark(rec, timestampMs)
            },
            onEditLabel = { timestampMs, newLabel ->
                viewModel.updateBookmarkLabel(rec, timestampMs, newLabel)
            }
        )
    }

    if (showAddBookmarkDialog && rec != null) {
        AlertDialog(
            onDismissRequest = { showAddBookmarkDialog = false },
            title = { Text("Add Bookmark") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current position: ${formatDuration(currentPosition)}")
                    OutlinedTextField(
                        value = bookmarkLabel,
                        onValueChange = { bookmarkLabel = it },
                        label = { Text("Bookmark label") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addBookmark(rec, currentPosition, bookmarkLabel)
                    bookmarkLabel = ""
                    showAddBookmarkDialog = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmarkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksBottomSheet(
    bookmarks: List<Bookmark>,
    duration: Long,
    onDismiss: () -> Unit,
    onSeek: (Long) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onEditLabel: (Long, String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bookmarks", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Button(onClick = onAdd, modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Current", fontSize = 12.sp)
                }
            }

            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No bookmarks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                bookmarks.sortedBy { it.timestampMs }.forEach { bm ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeek(bm.timestampMs) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bm.label.ifBlank { "Bookmark" },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = formatDuration(bm.timestampMs),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { onDelete(bm.timestampMs) }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.getDefault(), "%d:%02d", mins, secs)
}
