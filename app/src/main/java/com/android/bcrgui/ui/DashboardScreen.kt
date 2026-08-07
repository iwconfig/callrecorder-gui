package com.android.bcrgui.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.android.bcrgui.model.CallRecording
import java.util.Locale

private val GitHubIcon: ImageVector
    get() = ImageVector.Builder(
        name = "GitHub",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.477f, 2f, 2f, 6.477f, 2f, 12f)
            curveTo(2f, 16.42f, 4.87f, 20.17f, 8.84f, 21.5f)
            curveTo(9.34f, 21.58f, 9.5f, 21.27f, 9.5f, 21.0f)
            curveTo(9.5f, 20.77f, 9.5f, 20.0f, 9.5f, 19.14f)
            curveTo(6.72f, 19.74f, 6.13f, 18.15f, 6.13f, 18.15f)
            curveTo(5.68f, 17.0f, 5.03f, 16.7f, 5.03f, 16.7f)
            curveTo(4.12f, 16.08f, 5.1f, 16.1f, 5.1f, 16.1f)
            curveTo(6.1f, 16.17f, 6.63f, 17.13f, 6.63f, 17.13f)
            curveTo(7.53f, 18.66f, 8.97f, 18.22f, 9.54f, 17.97f)
            curveTo(9.63f, 17.32f, 9.89f, 16.88f, 10.18f, 16.63f)
            curveTo(7.96f, 16.38f, 5.63f, 15.52f, 5.63f, 11.69f)
            curveTo(5.63f, 10.6f, 6.02f, 9.7f, 6.66f, 9.0f)
            curveTo(6.56f, 8.75f, 6.21f, 7.73f, 6.76f, 6.35f)
            curveTo(6.76f, 6.35f, 7.6f, 6.08f, 9.5f, 7.37f)
            curveTo(10.3f, 7.15f, 11.15f, 7.04f, 12f, 7.04f)
            curveTo(12.85f, 7.04f, 13.7f, 7.15f, 14.5f, 7.37f)
            curveTo(16.4f, 6.08f, 17.24f, 6.35f, 17.24f, 6.35f)
            curveTo(17.79f, 7.73f, 17.44f, 8.75f, 17.34f, 9.0f)
            curveTo(17.98f, 9.7f, 18.37f, 10.6f, 18.37f, 11.69f)
            curveTo(18.37f, 15.53f, 16.04f, 16.38f, 13.81f, 16.63f)
            curveTo(14.17f, 16.94f, 14.5f, 17.56f, 14.5f, 18.51f)
            curveTo(14.5f, 19.87f, 14.5f, 20.97f, 14.5f, 21.3f)
            curveTo(14.5f, 21.57f, 14.66f, 21.89f, 15.17f, 21.79f)
            curveTo(19.14f, 20.46f, 22f, 16.71f, 22f, 12f)
            curveTo(22f, 6.477f, 17.52f, 2f, 12f, 2f)
            close()
        }
    }.build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenRecycleBin: () -> Unit
) {
    val recordings by viewModel.recordings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val directionFilter by viewModel.directionFilter.collectAsState()
    val simFilter by viewModel.simFilter.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val durationFilter by viewModel.durationFilter.collectAsState()
    val contactFilter by viewModel.contactFilter.collectAsState()
    val contactNames by viewModel.contactNames.collectAsState()
    val selectedRecording by viewModel.selectedRecording.collectAsState()
    val customStartDate by viewModel.customStartDate.collectAsState()
    val customEndDate by viewModel.customEndDate.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf<CallRecording?>(null) }
    var contextMenuRecording by remember { mutableStateOf<CallRecording?>(null) }
    var showFilterBottomSheet by remember { mutableStateOf(false) }

    var selectedRecordings by remember { mutableStateOf(emptySet<CallRecording>()) }
    val isMultiSelectMode = selectedRecordings.isNotEmpty()
    var showDeleteMultipleConfirmDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadContacts()
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        contactUri?.let { uri ->
            val projection = arrayOf(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameCol = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                        if (nameCol != -1) {
                            val name = cursor.getString(nameCol)
                            if (!name.isNullOrBlank()) {
                                viewModel.addContactFilter(name)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasContactsPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasContactsPermission) {
            contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (isMultiSelectMode) {
                        Text(
                            text = "${selectedRecordings.size} selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    } else {
                        Column {
                            Text(
                                text = "BCR Call Recordings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "${recordings.size} recordings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    val context = LocalContext.current
                    if (isMultiSelectMode) {
                        IconButton(onClick = { selectedRecordings = recordings.toSet() }) {
                            Icon(imageVector = Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { selectedRecordings = emptySet() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Deselect All")
                        }
                        IconButton(
                            onClick = {
                                val uris = ArrayList<Uri>(selectedRecordings.map { it.uri })
                                if (uris.isNotEmpty()) {
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND_MULTIPLE
                                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                        type = "audio/*"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share recordings"))
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share Selected")
                        }
                        IconButton(onClick = { showDeleteMultipleConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ArtRuntime/callrecorder-gui"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = GitHubIcon,
                                contentDescription = "GitHub Repository"
                            )
                        }
                        IconButton(onClick = onOpenRecycleBin) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Recycle Bin")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search Input - Circular and premium
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search recordings...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    // Filter Icon Button with badge if any filters are active
                    val hasActiveFilters = directionFilter != "all" || simFilter != null || dateFilter != "all" || durationFilter != "all"
                    val activeFiltersCount = (if (directionFilter != "all") 1 else 0) +
                            (if (simFilter != null) 1 else 0) +
                            (if (dateFilter != "all") 1 else 0) +
                            (if (durationFilter != "all") 1 else 0)

                    IconButton(
                        onClick = { showFilterBottomSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (hasActiveFilters) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                CircleShape
                            )
                    ) {
                        BadgedBox(
                            badge = {
                                if (hasActiveFilters) {
                                    Badge { Text(activeFiltersCount.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (hasActiveFilters) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Active Filters Row (only shown when any filter is active)
                val hasActiveFilters = directionFilter != "all" || simFilter != null || dateFilter != "all" || durationFilter != "all" || contactFilter.isNotEmpty()
                if (hasActiveFilters) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (directionFilter != "all") {
                            InputChip(
                                selected = true,
                                onClick = { viewModel.setDirectionFilter("all") },
                                label = { Text(if (directionFilter == "in") "Incoming" else "Outgoing") },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            )
                        }

                        if (simFilter != null) {
                            InputChip(
                                selected = true,
                                onClick = { viewModel.setSimFilter(null) },
                                label = { Text("SIM $simFilter") },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            )
                        }

                        if (dateFilter != "all") {
                            val dateLabel = when (dateFilter) {
                                "today" -> "Today"
                                "yesterday" -> "Yesterday"
                                "week" -> "Last 7 Days"
                                "older" -> "Older"
                                "custom" -> {
                                    if (customStartDate != null && customEndDate != null) {
                                        val formatter = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
                                        "${formatter.format(java.util.Date(customStartDate!!))} - ${formatter.format(java.util.Date(customEndDate!!))}"
                                    } else if (customStartDate != null) {
                                        val formatter = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
                                        "From ${formatter.format(java.util.Date(customStartDate!!))}"
                                    } else if (customEndDate != null) {
                                        val formatter = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
                                        "To ${formatter.format(java.util.Date(customEndDate!!))}"
                                    } else {
                                        "Custom Range"
                                    }
                                }
                                else -> dateFilter
                            }
                            InputChip(
                                selected = true,
                                onClick = { viewModel.setDateFilter("all") },
                                label = { Text(dateLabel) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            )
                        }

                        if (durationFilter != "all") {
                            val durationLabel = when (durationFilter) {
                                "short" -> "Short (<1m)"
                                "medium" -> "Medium (1-5m)"
                                "long" -> "Long (>5m)"
                                else -> durationFilter
                            }
                            InputChip(
                                selected = true,
                                onClick = { viewModel.setDurationFilter("all") },
                                label = { Text(durationLabel) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            )
                        }

                        if (contactFilter.isNotEmpty()) {
                            contactFilter.forEach { name ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.removeContactFilter(name) },
                                    label = { Text(name) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                )
                            }
                        }

                        // Clear All Button
                        TextButton(
                            onClick = {
                                viewModel.setDirectionFilter("all")
                                viewModel.setSimFilter(null)
                                viewModel.setDateFilter("all")
                                viewModel.setDurationFilter("all")
                                viewModel.clearContactFilters()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear All", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Main Content with PullToRefreshBox
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.loadRecordings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (recordings.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator()
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Inbox,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "No Recordings Found",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Verify your selected folder and filename template.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recordings) { rec ->
                                RecordingItem(
                                    recording = rec,
                                    isChecked = selectedRecordings.contains(rec),
                                    isPlaying = selectedRecording?.uri == rec.uri,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            selectedRecordings = if (selectedRecordings.contains(rec)) {
                                                selectedRecordings - rec
                                            } else {
                                                selectedRecordings + rec
                                            }
                                        } else {
                                            viewModel.selectRecording(rec)
                                        }
                                    },
                                    onLongClick = {
                                        selectedRecordings = if (selectedRecordings.contains(rec)) {
                                            selectedRecordings - rec
                                        } else {
                                            selectedRecordings + rec
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Dropdown Menu for Context Operations
            contextMenuRecording?.let { rec ->
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { contextMenuRecording = null }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete Recording") },
                        onClick = {
                            showDeleteConfirmDialog = rec
                            contextMenuRecording = null
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                     if (rec.transcriptionStatus != "completed") {
                         val isRemote = viewModel.aiServerUrl.collectAsState().value.isNotBlank()
                         DropdownMenuItem(
                             text = { Text(if (isRemote) "Transcribe (Remote)" else "Transcribe (On-device)") },
                             onClick = {
                                 viewModel.transcribeRecording(rec)
                                 contextMenuRecording = null
                                 android.widget.Toast.makeText(context, "Transcription started", android.widget.Toast.LENGTH_SHORT).show()
                             },
                             leadingIcon = {
                                 Icon(
                                     imageVector = Icons.Default.GraphicEq,
                                     contentDescription = null,
                                     tint = MaterialTheme.colorScheme.primary
                                 )
                             }
                         )
                     }
                 }
             }

            // Delete Confirmation Dialog (Single Item)
            showDeleteConfirmDialog?.let { rec ->
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = null },
                    title = { Text("Delete Recording?") },
                    text = { Text("Are you sure you want to move the recording for '${rec.resolvedName}' to the Recycle Bin? You can restore or permanently delete it later from settings.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteRecording(rec)
                                showDeleteConfirmDialog = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Delete Selected Confirmation Dialog (Multiple Items)
            if (showDeleteMultipleConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteMultipleConfirmDialog = false },
                    title = { Text("Delete Selected Recordings?") },
                    text = { Text("Are you sure you want to move the ${selectedRecordings.size} selected recordings to the Recycle Bin? You can restore or permanently delete them later from settings.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                selectedRecordings.forEach { rec ->
                                    viewModel.deleteRecording(rec)
                                }
                                selectedRecordings = emptySet()
                                showDeleteMultipleConfirmDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteMultipleConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Filter Options Bottom Sheet
            if (showFilterBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showFilterBottomSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    var tempContactFilter by remember(showFilterBottomSheet, contactFilter) { mutableStateOf("") }
                    var tempSelectedContacts by remember(showFilterBottomSheet, contactFilter) { mutableStateOf(contactFilter) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filter Recordings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            val hasActive = directionFilter != "all" || simFilter != null || dateFilter != "all" || durationFilter != "all" || contactFilter.isNotEmpty()
                            if (hasActive) {
                                TextButton(
                                    onClick = {
                                        viewModel.setDirectionFilter("all")
                                        viewModel.setSimFilter(null)
                                        viewModel.setDateFilter("all")
                                        viewModel.setDurationFilter("all")
                                        viewModel.clearContactFilters()
                                        tempSelectedContacts = emptyList()
                                        tempContactFilter = ""
                                    }
                                ) {
                                    Text("Reset All", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Filter Group 1: Direction
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Call Direction", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "all" to "All",
                                    "in" to "Incoming",
                                    "out" to "Outgoing"
                                ).forEach { (value, label) ->
                                    val isSelected = directionFilter == value
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setDirectionFilter(value) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        // Filter Group 2: SIM Card
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SIM Slot", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    null to "Both SIMs",
                                    1 to "SIM 1",
                                    2 to "SIM 2"
                                ).forEach { (value, label) ->
                                    val isSelected = simFilter == value
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setSimFilter(value) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        // Filter Group 3: Date
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Date Recorded", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "all" to "All Dates",
                                    "today" to "Today",
                                    "yesterday" to "Yesterday",
                                    "week" to "Last 7 Days",
                                    "older" to "Older",
                                    "custom" to "Custom Range"
                                ).forEach { (value, label) ->
                                    val isSelected = dateFilter == value
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setDateFilter(value) },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            if (dateFilter == "custom") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val startText = remember(customStartDate) {
                                        if (customStartDate != null) {
                                            val formatter = java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.getDefault())
                                            formatter.format(java.util.Date(customStartDate!!))
                                        } else {
                                            "Select Start Time"
                                        }
                                    }
                                    val endText = remember(customEndDate) {
                                        if (customEndDate != null) {
                                            val formatter = java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.getDefault())
                                            formatter.format(java.util.Date(customEndDate!!))
                                        } else {
                                            "Select End Time"
                                        }
                                    }

                                    OutlinedCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                showDateTimePicker(context, customStartDate) { time ->
                                                    viewModel.setCustomDateRange(time, customEndDate)
                                                }
                                            },
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = if (customStartDate != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Column {
                                                Text("Start Time", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(startText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }

                                    OutlinedCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                showDateTimePicker(context, customEndDate) { time ->
                                                    viewModel.setCustomDateRange(customStartDate, time)
                                                }
                                            },
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = if (customEndDate != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Column {
                                                Text("End Time", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(endText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Filter Group 4: Call Duration
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Call Length", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "all" to "All Lengths",
                                    "short" to "Short (< 1m)",
                                    "medium" to "Medium (1-5m)",
                                    "long" to "Long (> 5m)"
                                ).forEach { (value, label) ->
                                    val isSelected = durationFilter == value
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setDurationFilter(value) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        // Filter Group 5: Specific Contact Name or Phone
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Specific Contact", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            
                            // Selected contacts chips
                            if (tempSelectedContacts.isNotEmpty()) {
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    tempSelectedContacts.forEach { name ->
                                        InputChip(
                                            selected = true,
                                            onClick = { tempSelectedContacts = tempSelectedContacts - name },
                                            label = { Text(name, fontSize = 12.sp) },
                                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
                                        )
                                    }
                                }
                            }
                            
                            val suggestions = remember(tempContactFilter, contactNames, tempSelectedContacts) {
                                if (tempContactFilter.isBlank()) {
                                    emptyList()
                                } else {
                                    val q = tempContactFilter.lowercase().trim()
                                    contactNames.filter { 
                                        it.lowercase().contains(q) && !tempSelectedContacts.contains(it)
                                    }.take(5)
                                }
                            }
                            var showSuggestionsDropdown by remember { mutableStateOf(false) }
                            
                            LaunchedEffect(tempContactFilter) {
                                showSuggestionsDropdown = tempContactFilter.isNotBlank()
                            }
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = tempContactFilter,
                                    onValueChange = { 
                                        tempContactFilter = it
                                        showSuggestionsDropdown = true
                                    },
                                    placeholder = { Text("Add contact...") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (tempContactFilter.isNotBlank()) {
                                                IconButton(onClick = { tempContactFilter = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                                }
                                            }
                                            IconButton(onClick = { contactPickerLauncher.launch(null) }) {
                                                Icon(Icons.Default.Contacts, contentDescription = "Select Contact")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    maxLines = 1,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                
                                DropdownMenu(
                                    expanded = showSuggestionsDropdown && suggestions.isNotEmpty(),
                                    onDismissRequest = { showSuggestionsDropdown = false },
                                    properties = PopupProperties(focusable = false),
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f),
                                    shape = RoundedCornerShape(16.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    suggestions.forEachIndexed { index, name ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            leadingIcon = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = name.first().uppercase(),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            },
                                            onClick = {
                                                tempSelectedContacts = tempSelectedContacts + name
                                                tempContactFilter = ""
                                                showSuggestionsDropdown = false
                                            }
                                        )
                                        if (index < suggestions.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                viewModel.clearContactFilters()
                                tempSelectedContacts.forEach { viewModel.addContactFilter(it) }
                                showFilterBottomSheet = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Apply Filters", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingItem(
    recording: CallRecording,
    isChecked: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            } else if (isPlaying) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Icon Avatar
            val directionColor = when (recording.direction?.lowercase()) {
                "in" -> Color(0xFF4CAF50) // Green
                "out" -> Color(0xFF6200EE) // Purple
                else -> Color(0xFFFF9800) // Orange (Conference/other)
            }
            val directionIcon = when (recording.direction?.lowercase()) {
                "in" -> Icons.AutoMirrored.Default.CallReceived
                "out" -> Icons.AutoMirrored.Default.CallMade
                else -> Icons.Default.Call
            }

            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (isChecked) {
                                MaterialTheme.colorScheme.primary
                            } else if (isPlaying) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                directionColor.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isChecked) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (isPlaying) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.VolumeUp,
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = directionIcon,
                            contentDescription = null,
                            tint = directionColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = recording.resolvedName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                recording.resolvedSubtext?.let { sub ->
                    Text(
                        text = sub,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Date, Time and optional App source package badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = recording.formattedDateTime,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )

                    if (recording.packageName != null && recording.packageName != "com.android.phone") {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = recording.packageName.substringAfterLast('.').uppercase(),
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Info (SIM details, duration, size)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (recording.simSlot != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SIM ${recording.simSlot}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (recording.hasMetadataJson) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Metadata JSON Loaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (recording.transcriptionStatus == "completed") {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Transcription Completed",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (recording.bookmarkCount > 0) {
                        Text(
                            text = recording.bookmarkCount.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }

                Text(
                    text = formatDuration(recording.durationMs),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatSize(recording.size),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.getDefault(), "%d:%02d", mins, secs)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        Locale.getDefault(),
        "%.1f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups]
    )
}

private fun showDateTimePicker(
    context: android.content.Context,
    initialTimestamp: Long?,
    onDateTimeSelected: (Long) -> Unit
) {
    val calendar = java.util.Calendar.getInstance()
    if (initialTimestamp != null) {
        calendar.timeInMillis = initialTimestamp
    }

    android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(java.util.Calendar.YEAR, year)
            calendar.set(java.util.Calendar.MONTH, month)
            calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)

            android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(java.util.Calendar.MINUTE, minute)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    onDateTimeSelected(calendar.timeInMillis)
                },
                calendar.get(java.util.Calendar.HOUR_OF_DAY),
                calendar.get(java.util.Calendar.MINUTE),
                false
            ).show()
        },
        calendar.get(java.util.Calendar.YEAR),
        calendar.get(java.util.Calendar.MONTH),
        calendar.get(java.util.Calendar.DAY_OF_MONTH)
    ).show()
}
