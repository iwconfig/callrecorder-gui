package com.android.bcrgui.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    currentFolderUri: String?,
    currentTemplate: String,
    currentExtension: String,
    currentAccentColor: String,
    currentAmoledMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (folderUri: String?, template: String, extension: String, accentColor: String, amoledMode: Boolean) -> Unit,
    onSaveAi: (serverUrl: String, model: String, autoTranscribe: Boolean, llmProvider: String) -> Unit,
    onResetOnboarding: () -> Unit
) {
    val context = LocalContext.current
    var tempFolderUri by remember { mutableStateOf(currentFolderUri) }
    var tempTemplate by remember { mutableStateOf(currentTemplate) }
    var tempExtension by remember { mutableStateOf(currentExtension) }
    var tempAccentColor by remember { mutableStateOf(currentAccentColor) }
    var tempAmoledMode by remember { mutableStateOf(currentAmoledMode) }

    val aiServerUrl by viewModel.aiServerUrl.collectAsState()
    val aiModel by viewModel.aiModel.collectAsState()
    val aiAutoTranscribe by viewModel.aiAutoTranscribe.collectAsState()
    val aiLlmProvider by viewModel.aiLlmProvider.collectAsState()

    var tempAiServerUrl by remember { mutableStateOf(aiServerUrl) }
    var tempAiModel by remember { mutableStateOf(aiModel) }
    var tempAiAutoTranscribe by remember { mutableStateOf(aiAutoTranscribe) }
    var tempAiLlmProvider by remember { mutableStateOf(aiLlmProvider) }

    var showResetConfirm by remember { mutableStateOf(false) }
    var showRecycleBinDialog by remember { mutableStateOf(false) }

    val recycledFiles by viewModel.recycledFiles.collectAsState()
    val recycledCount = recycledFiles.size
    val recycledTotalSizeBytes = recycledFiles.sumOf { it.size }
    
    val recycledSizeFormatted = remember(recycledTotalSizeBytes) {
        val sizeInMb = recycledTotalSizeBytes.toDouble() / (1024 * 1024)
        if (sizeInMb < 0.1) {
            String.format("%.2f KB", recycledTotalSizeBytes.toDouble() / 1024)
        } else {
            String.format("%.2f MB", sizeInMb)
        }
    }

    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    val extensionOptions = listOf("all", ".oga", ".ogg", ".m4a", ".flac", ".wav", ".mp3", ".3gp", ".amr")

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                tempFolderUri = it.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Application?") },
            text = { Text("This will clear all configuration settings, folder connections, and restart the onboarding wizard. Audio files will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetOnboarding()
                        showResetConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configure Player Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Recording Folder",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect Different Folder")
                    }
                    Text(
                        text = "URI: ${tempFolderUri?.let { Uri.parse(it).path } ?: "None Linked"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Section 2: Filename Template Schema
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Filename Template Schema",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = tempTemplate,
                        onValueChange = { tempTemplate = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = false,
                        maxLines = 3
                    )
                }

                // Section 3: File extension chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Target File Extension",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        extensionOptions.take(5).forEach { ext ->
                            val isSelected = tempExtension == ext
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { tempExtension = ext }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (ext == "all") "All Audio" else ext,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        extensionOptions.drop(5).forEach { ext ->
                            val isSelected = tempExtension == ext
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { tempExtension = ext }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (ext == "all") "All Audio" else ext,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Theme & Customization",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "Accent Color",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // System Dynamic (Material You Wallpaper Accent) for API >= 31
                        val isSystemSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                        if (isSystemSupported) {
                            val isSystemSelected = tempAccentColor == "system"
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = if (isSystemSelected) 3.dp else 1.dp,
                                        color = if (isSystemSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { tempAccentColor = "system" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = "System Wallpaper Color",
                                    tint = if (isSystemSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        listOf(
                            "purple" to Color(0xFF8B5CF6),
                            "blue" to Color(0xFF3B82F6),
                            "teal" to Color(0xFF14B8A6),
                            "green" to Color(0xFF10B981),
                            "orange" to Color(0xFFF97316),
                            "red" to Color(0xFFEF4444),
                            "pink" to Color(0xFFEC4899)
                        ).forEach { (name, color) ->
                            val isSelected = tempAccentColor == name
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { tempAccentColor = name }
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { tempAmoledMode = !tempAmoledMode }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AMOLED Theme",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Pure black backgrounds in dark mode to save OLED battery.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tempAmoledMode,
                            onCheckedChange = { tempAmoledMode = it },
                            thumbContent = if (tempAmoledMode) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            } else null
                        )
                    }
                }

                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "AI Transcription & Metadata",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = tempAiServerUrl,
                        onValueChange = { tempAiServerUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        placeholder = { Text("Remote server URL (leave empty for on-device)") },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-transcribe new recordings",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Automatically start transcription after selection",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tempAiAutoTranscribe,
                            onCheckedChange = { tempAiAutoTranscribe = it },
                            thumbContent = if (tempAiAutoTranscribe) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            } else null
                        )
                    }
                }

                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Storage & Cleanup",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { showRecycleBinDialog = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recycle Bin",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Deleted recordings are stored here. Total: $recycledCount files ($recycledSizeFormatted)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "View Recycle Bin",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider()

                // Section 5: OSS & About
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ArtRuntime/callrecorder-gui"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Open Source Project",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "github.com/ArtRuntime/callrecorder-gui",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Version $versionName",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Light
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reset Wizard Button
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Text("Reset Onboarding Wizard")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(tempFolderUri, tempTemplate, tempExtension, tempAccentColor, tempAmoledMode)
                    onSaveAi(tempAiServerUrl, tempAiModel, tempAiAutoTranscribe, tempAiLlmProvider)
                    onDismiss()
                },
                enabled = tempFolderUri != null && tempTemplate.isNotBlank()
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showRecycleBinDialog) {
        RecycleBinDialog(
            viewModel = viewModel,
            onDismiss = { showRecycleBinDialog = false }
        )
    }
}

@Composable
fun RecycleBinDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val recycledFiles by viewModel.recycledFiles.collectAsState()
    val recycledCount = recycledFiles.size
    val recycledTotalSizeBytes = recycledFiles.sumOf { it.size }
    
    val recycledSizeFormatted = remember(recycledTotalSizeBytes) {
        val sizeInMb = recycledTotalSizeBytes.toDouble() / (1024 * 1024)
        if (sizeInMb < 0.1) {
            String.format("%.2f KB", recycledTotalSizeBytes.toDouble() / 1024)
        } else {
            String.format("%.2f MB", sizeInMb)
        }
    }
    
    var selectedFiles by remember { mutableStateOf(emptySet<String>()) }
    var showEmptyConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirmDialog by remember { mutableStateOf(false) }

    if (showEmptyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirmDialog = false },
            title = { Text("Empty Recycle Bin?") },
            text = { Text("Are you sure you want to permanently delete all items in the Recycle Bin? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.emptyRecycleBin()
                        selectedFiles = emptySet()
                        showEmptyConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Empty")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteSelectedConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirmDialog = false },
            title = { Text("Delete Selected?") },
            text = { Text("Are you sure you want to permanently delete the selected items? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedFiles.forEach { name ->
                            viewModel.deletePermanently(name)
                        }
                        selectedFiles = emptySet()
                        showDeleteSelectedConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recycle Bin", fontWeight = FontWeight.Bold)
                if (recycledFiles.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            if (selectedFiles.size == recycledFiles.size) {
                                selectedFiles = emptySet()
                            } else {
                                selectedFiles = recycledFiles.map { it.name }.toSet()
                            }
                        }
                    ) {
                        Text(
                            text = if (selectedFiles.size == recycledFiles.size) "Deselect All" else "Select All",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedFiles.isNotEmpty()) {
                            "${selectedFiles.size} selected"
                        } else {
                            "$recycledCount files ($recycledSizeFormatted)"
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (selectedFiles.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    selectedFiles.forEach { name ->
                                        viewModel.restoreRecycledFile(name)
                                    }
                                    selectedFiles = emptySet()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Restore Selected", fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = {
                                    showDeleteSelectedConfirmDialog = true
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Delete Selected", fontSize = 11.sp)
                            }
                        } else if (recycledCount > 0) {
                            TextButton(
                                onClick = {
                                    showEmptyConfirmDialog = true
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Empty Bin", fontSize = 11.sp)
                            }
                        }
                    }
                }
                
                if (recycledFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Recycle bin is empty",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recycledFiles.forEach { file ->
                            val isChecked = selectedFiles.contains(file.name)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFiles = if (isChecked) {
                                            selectedFiles - file.name
                                        } else {
                                            selectedFiles + file.name
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isChecked) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedFiles = if (checked == true) {
                                                selectedFiles + file.name
                                            } else {
                                                selectedFiles - file.name
                                            }
                                        },
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.resolvedName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        
                                        val fileSizeMb = file.size.toDouble() / (1024 * 1024)
                                        val sizeStr = if (fileSizeMb < 0.1) String.format("%.1f KB", file.size.toDouble() / 1024) else String.format("%.1f MB", fileSizeMb)
                                        
                                        val subtext = remember(file.resolvedSubtext, sizeStr) {
                                            if (file.resolvedSubtext != null) {
                                                "${file.resolvedSubtext} • $sizeStr"
                                            } else {
                                                sizeStr
                                            }
                                        }
                                        
                                        Text(
                                            text = subtext,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.restoreRecycledFile(file.name) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Restore,
                                                contentDescription = "Restore",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deletePermanently(file.name) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Permanently",
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
