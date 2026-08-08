package com.android.bcrgui.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.bcrgui.model.CallRecording
import com.android.bcrgui.model.RecordingRepository
import com.android.bcrgui.parser.BcrTemplateParser
import com.android.bcrgui.player.BcrAudioPlayer
import com.android.bcrgui.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.android.bcrgui.model.Bookmark
import com.android.bcrgui.model.BookmarkRepository
import com.android.bcrgui.transcription.TranscriptionWorker
import com.android.bcrgui.transcription.TranscriptRepository
import com.android.bcrgui.transcription.MetadataRepository

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val repository = RecordingRepository(application)
    private val bookmarkRepository = BookmarkRepository(application)
    private val transcriptRepository = TranscriptRepository(application)
    private val metadataRepository = MetadataRepository(application)
    val audioPlayer = BcrAudioPlayer(application)
    private val workManager = WorkManager.getInstance(application)

    // Configuration States
    private val _folderUri = MutableStateFlow<String?>(prefs.folderUri)
    val folderUri: StateFlow<String?> = _folderUri

    private val _filenameTemplate = MutableStateFlow(prefs.filenameTemplate)
    val filenameTemplate: StateFlow<String> = _filenameTemplate

    private val _fileExtension = MutableStateFlow(prefs.fileExtension)
    val fileExtension: StateFlow<String> = _fileExtension

    private val _isOnboardingCompleted = MutableStateFlow(prefs.isOnboardingCompleted)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted

    private val _accentColor = MutableStateFlow(prefs.accentColor)
    val accentColor: StateFlow<String> = _accentColor

    private val _amoledMode = MutableStateFlow(prefs.amoledMode)
    val amoledMode: StateFlow<Boolean> = _amoledMode

    private val _aiServerUrl = MutableStateFlow(prefs.aiServerUrl)
    val aiServerUrl: StateFlow<String> = _aiServerUrl

    private val _aiModel = MutableStateFlow(prefs.aiModel)
    val aiModel: StateFlow<String> = _aiModel

    private val _aiAutoTranscribe = MutableStateFlow(prefs.aiAutoTranscribe)
    val aiAutoTranscribe: StateFlow<Boolean> = _aiAutoTranscribe

    private val _aiLlmProvider = MutableStateFlow(prefs.aiLlmProvider)
    val aiLlmProvider: StateFlow<String> = _aiLlmProvider

    // UI States
    private val _rawRecordings = MutableStateFlow<List<CallRecording>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _directionFilter = MutableStateFlow("all") // all, in, out
    val directionFilter: StateFlow<String> = _directionFilter

    private val _simFilter = MutableStateFlow<Int?>(null) // null (all), 1, 2
    val simFilter: StateFlow<Int?> = _simFilter

    private val _durationFilter = MutableStateFlow("all") // all, short, medium, long
    val durationFilter: StateFlow<String> = _durationFilter

    private val _dateFilter = MutableStateFlow("all") // all, today, yesterday, week, older, custom
    val dateFilter: StateFlow<String> = _dateFilter

    private val _customStartDate = MutableStateFlow<Long?>(null)
    val customStartDate: StateFlow<Long?> = _customStartDate

    private val _customEndDate = MutableStateFlow<Long?>(null)
    val customEndDate: StateFlow<Long?> = _customEndDate

    private val _contactFilter = MutableStateFlow<List<String>>(emptyList())
    val contactFilter: StateFlow<List<String>> = _contactFilter

    private val _selectedRecording = MutableStateFlow<CallRecording?>(null)
    val selectedRecording: StateFlow<CallRecording?> = _selectedRecording

    private val _recycledFiles = MutableStateFlow<List<com.android.bcrgui.model.RecycledFile>>(emptyList())
    val recycledFiles: StateFlow<List<com.android.bcrgui.model.RecycledFile>> = _recycledFiles

    private val _contactNames = MutableStateFlow<List<String>>(emptyList())
    val contactNames: StateFlow<List<String>> = _contactNames

    private val _selectedTranscript = MutableStateFlow<com.android.bcrgui.model.AiTranscription?>(null)
    val selectedTranscript: StateFlow<com.android.bcrgui.model.AiTranscription?> = _selectedTranscript

    private val _selectedMetadata = MutableStateFlow<com.android.bcrgui.model.AiMetadata?>(null)
    val selectedMetadata: StateFlow<com.android.bcrgui.model.AiMetadata?> = _selectedMetadata

    private val _selectedBookmarks = MutableStateFlow<List<com.android.bcrgui.model.Bookmark>>(emptyList())
    val selectedBookmarks: StateFlow<List<com.android.bcrgui.model.Bookmark>> = _selectedBookmarks

    private var contactsMap = emptyMap<String, String>()

    // Combine filters and search query with raw recordings
    @Suppress("UNCHECKED_CAST")
    val recordings: StateFlow<List<CallRecording>> = combine(
        _rawRecordings,
        _searchQuery,
        _directionFilter,
        _simFilter,
        _durationFilter,
        _dateFilter,
        _contactFilter,
        _customStartDate,
        _customEndDate
    ) { flows ->
        val raw = flows[0] as List<CallRecording>
        val query = flows[1] as String
        val direction = flows[2] as String
        val sim = flows[3] as Int?
        val durationRange = flows[4] as String
        val dateRange = flows[5] as String
        val contactVal = flows[6] as List<String>
        val customStart = flows[7] as Long?
        val customEnd = flows[8] as Long?

        var list = raw

        // Filter by specific contacts (match ANY selected contact)
        if (contactVal.isNotEmpty()) {
            list = list.filter { recording ->
                contactVal.any { cf ->
                    val cfLower = cf.lowercase().trim()
                    recording.resolvedName.lowercase().contains(cfLower) ||
                            (recording.phoneNumber ?: "").lowercase().contains(cfLower)
                }
            }
        }

        // Filter by search query
        if (query.isNotBlank()) {
            val q = query.lowercase().trim()
            list = list.filter {
                it.resolvedName.lowercase().contains(q) ||
                        (it.phoneNumber ?: "").lowercase().contains(q) ||
                        (it.date ?: "").lowercase().contains(q)
            }
        }

        // Filter by direction
        if (direction != "all") {
            list = list.filter { it.direction?.lowercase() == direction }
        }


        if (sim != null) {
            list = list.filter { it.simSlot == sim }
        }

        // Filter by duration range
        if (durationRange != "all") {
            list = when (durationRange) {
                "short" -> list.filter { it.durationMs < 60000L } // < 1 min
                "medium" -> list.filter { it.durationMs in 60000L..300000L } // 1 - 5 mins
                "long" -> list.filter { it.durationMs > 300000L } // > 5 mins
                else -> list
            }
        }
        if (dateRange != "all") {
            if (dateRange == "custom") {
                if (customStart != null) {
                    list = list.filter { it.lastModified >= customStart }
                }
                if (customEnd != null) {
                    list = list.filter { it.lastModified <= customEnd }
                }
            } else {
                val now = System.currentTimeMillis()
                val dayMs = 24 * 60 * 60 * 1000L
                list = when (dateRange) {
                    "today" -> list.filter { now - it.lastModified < dayMs }
                    "yesterday" -> list.filter {
                        val age = now - it.lastModified
                        age in dayMs..(dayMs * 2)
                    }
                    "week" -> list.filter { now - it.lastModified < dayMs * 7 }
                    "older" -> list.filter { now - it.lastModified >= dayMs * 7 }
                    else -> list
                }
            }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Player State Flows delegated from audioPlayer
    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val currentPosition: StateFlow<Long> = audioPlayer.currentPosition
    val duration: StateFlow<Long> = audioPlayer.duration
    val playbackSpeed: StateFlow<Float> = audioPlayer.playbackSpeed

    fun setDurationFilter(filter: String) {
        _durationFilter.value = filter
    }

    fun setDateFilter(filter: String) {
        _dateFilter.value = filter
        if (filter != "custom") {
            _customStartDate.value = null
            _customEndDate.value = null
        }
    }

    fun setCustomDateRange(start: Long?, end: Long?) {
        _customStartDate.value = start
        _customEndDate.value = end
        _dateFilter.value = if (start != null || end != null) "custom" else "all"
    }

    fun addContactFilter(name: String) {
        if (name.isNotBlank() && !_contactFilter.value.contains(name)) {
            _contactFilter.value = _contactFilter.value + name
        }
    }

    fun removeContactFilter(name: String) {
        _contactFilter.value = _contactFilter.value - name
    }

    fun clearContactFilters() {
        _contactFilter.value = emptyList()
    }

    fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, String>()
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    getApplication(),
                    android.Manifest.permission.READ_CONTACTS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                try {
                    getApplication<Application>().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        val numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            val number = cursor.getString(numberIndex)
                            val name = cursor.getString(nameIndex)
                            if (number != null && name != null) {
                                val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(number)
                                if (normalized.isNotEmpty()) {
                                    map[normalized] = name
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            contactsMap = map
            _contactNames.value = map.values.distinct().sorted()
            // Trigger list refresh to apply contact names to existing raw recordings
            if (_rawRecordings.value.isNotEmpty()) {
                val updatedList = _rawRecordings.value.map { recording ->
                    val phone = recording.phoneNumber
                    if (phone != null) {
                        val normalizedPhone = android.telephony.PhoneNumberUtils.normalizeNumber(phone)
                        val systemName = contactsMap[normalizedPhone] ?: contactsMap[phone]
                        if (systemName != null) {
                            recording.copy(contactName = systemName)
                        } else {
                            recording
                        }
                    } else {
                        recording
                    }
                }
                _rawRecordings.value = updatedList
            }
            loadRecycledFiles()
        }
    }

    fun loadRecycledFiles() {
        viewModelScope.launch {
            val template = _filenameTemplate.value
            _recycledFiles.value = repository.getRecycledRecordings(template, contactsMap)
        }
    }

    init {
        loadContacts()
        loadRecycledFiles()
        if (prefs.isOnboardingCompleted && !prefs.folderUri.isNullOrEmpty()) {
            loadRecordings()
        }
    }

    fun loadRecordings() {
        val folder = _folderUri.value ?: return
        val template = _filenameTemplate.value
        val ext = _fileExtension.value

        viewModelScope.launch {
            _isLoading.value = true
            try {
                var list = repository.getRecordings(folder, template, ext)
                if (contactsMap.isNotEmpty()) {
                    list = list.map { recording ->
                        val phone = recording.phoneNumber
                        if (phone != null) {
                            val normalizedPhone = android.telephony.PhoneNumberUtils.normalizeNumber(phone)
                            val systemName = contactsMap[normalizedPhone] ?: contactsMap[phone]
                            if (systemName != null) {
                                recording.copy(contactName = systemName)
                            } else {
                                recording
                            }
                        } else {
                            recording
                        }
                    }
                }
                _rawRecordings.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveSettings(folder: String?, template: String, extension: String, accent: String, amoled: Boolean) {
        prefs.folderUri = folder
        prefs.filenameTemplate = template
        prefs.fileExtension = extension
        prefs.accentColor = accent
        prefs.amoledMode = amoled

        _folderUri.value = folder
        _filenameTemplate.value = template
        _fileExtension.value = extension
        _accentColor.value = accent
        _amoledMode.value = amoled

        loadRecordings()
    }

    fun saveAiSettings(serverUrl: String, model: String, autoTranscribe: Boolean, llmProvider: String) {
        prefs.aiServerUrl = serverUrl
        prefs.aiModel = model
        prefs.aiAutoTranscribe = autoTranscribe
        prefs.aiLlmProvider = llmProvider

        _aiServerUrl.value = serverUrl
        _aiModel.value = model
        _aiAutoTranscribe.value = autoTranscribe
        _aiLlmProvider.value = llmProvider
    }

    fun transcribeRecording(recording: CallRecording) {
        val folder = _folderUri.value ?: return
        val dotIndex = recording.displayName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) recording.displayName.substring(0, dotIndex) else recording.displayName

        android.util.Log.d("MainViewModel", "transcribeRecording: baseName=$baseName, serverUrl=${_aiServerUrl.value}, useRemote=${_aiServerUrl.value.isNotBlank()}")

        val inputData = androidx.work.Data.Builder()
            .putString(TranscriptionWorker.KEY_FOLDER_URI, folder)
            .putString(TranscriptionWorker.KEY_BASE_NAME, baseName)
            .putString(TranscriptionWorker.KEY_AUDIO_URI, recording.uri.toString())
            .putString(TranscriptionWorker.KEY_MODEL_NAME, _aiModel.value)
            .putString(TranscriptionWorker.KEY_SERVER_URL, _aiServerUrl.value)
            .putBoolean(TranscriptionWorker.KEY_USE_REMOTE, _aiServerUrl.value.isNotBlank())
            .build()

        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(
            "transcribe_$baseName",
            ExistingWorkPolicy.REPLACE,
            request
        )

        android.util.Log.d("MainViewModel", "Enqueued transcription work: transcribe_$baseName")
        loadRecordings()

        // Reload transcript/metadata after the worker finishes
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _selectedRecording.value?.let { rec ->
                android.util.Log.d("MainViewModel", "Delayed reload of transcript/metadata for ${rec.displayName}")
                loadSelectedTranscript()
                loadSelectedMetadata()
            }
        }
    }

    fun loadSelectedTranscript() {
        val recording = _selectedRecording.value ?: return
        val folder = _folderUri.value ?: return
        val dotIndex = recording.displayName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) recording.displayName.substring(0, dotIndex) else recording.displayName

        android.util.Log.d("MainViewModel", "loadSelectedTranscript: baseName=$baseName, folder=$folder")
        viewModelScope.launch {
            val transcript = transcriptRepository.getTranscript(folder, baseName)
            android.util.Log.d("MainViewModel", "loadSelectedTranscript result: ${transcript?.text?.take(100)}")
            _selectedTranscript.value = transcript
        }
    }

    fun loadSelectedMetadata() {
        val recording = _selectedRecording.value ?: return
        val folder = _folderUri.value ?: return
        val dotIndex = recording.displayName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) recording.displayName.substring(0, dotIndex) else recording.displayName

        android.util.Log.d("MainViewModel", "loadSelectedMetadata: baseName=$baseName, folder=$folder")
        viewModelScope.launch {
            val metadata = metadataRepository.getMetadata(folder, baseName)
            android.util.Log.d("MainViewModel", "loadSelectedMetadata result: summary=${metadata?.summary?.take(100)}")
            _selectedMetadata.value = metadata
        }
    }

    fun loadSelectedBookmarks() {
        val recording = _selectedRecording.value ?: return
        val folder = _folderUri.value ?: return
        val dotIndex = recording.displayName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) recording.displayName.substring(0, dotIndex) else recording.displayName

        viewModelScope.launch {
            val bookmarks = bookmarkRepository.getBookmarks(folder, baseName)
            _selectedBookmarks.value = bookmarks
        }
    }

    fun addBookmark(recording: CallRecording, timestampMs: Long, label: String) {
        val folder = _folderUri.value ?: return
        val dotIndex = recording.displayName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) recording.displayName.substring(0, dotIndex) else recording.displayName

        viewModelScope.launch {
            val bookmarks = bookmarkRepository.getBookmarks(folder, baseName).toMutableList()
            bookmarks.add(com.android.bcrgui.model.Bookmark(timestampMs = timestampMs, label = label))
            bookmarkRepository.saveBookmarks(folder, baseName, bookmarks)
            _selectedBookmarks.value = bookmarks
            loadRecordings()
        }
    }

    fun deleteBookmark(recording: CallRecording, timestampMs: Long) {
        val folder = _folderUri.value ?: return
        val dotIndex = recording.displayName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) recording.displayName.substring(0, dotIndex) else recording.displayName

        viewModelScope.launch {
            val bookmarks = bookmarkRepository.getBookmarks(folder, baseName)
                .filter { it.timestampMs != timestampMs }
            bookmarkRepository.saveBookmarks(folder, baseName, bookmarks)
            _selectedBookmarks.value = bookmarks
            loadRecordings()
        }
    }

    fun updateBookmarkLabel(recording: CallRecording, timestampMs: Long, newLabel: String) {
        val folder = _folderUri.value ?: return
        val dotIndex = recording.displayName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) recording.displayName.substring(0, dotIndex) else recording.displayName

        viewModelScope.launch {
            val bookmarks = bookmarkRepository.getBookmarks(folder, baseName).map {
                if (it.timestampMs == timestampMs) it.copy(label = newLabel) else it
            }
            bookmarkRepository.saveBookmarks(folder, baseName, bookmarks)
            _selectedBookmarks.value = bookmarks
        }
    }

    fun completeOnboarding(folder: String, template: String, extension: String) {
        saveSettings(folder, template, extension, PreferencesManager.DEFAULT_ACCENT_COLOR, false)
        prefs.isOnboardingCompleted = true
        _isOnboardingCompleted.value = true
    }

    fun resetOnboarding() {
        prefs.clear()
        _folderUri.value = null
        _filenameTemplate.value = PreferencesManager.DEFAULT_TEMPLATE
        _fileExtension.value = PreferencesManager.DEFAULT_EXTENSION
        _accentColor.value = PreferencesManager.DEFAULT_ACCENT_COLOR
        _amoledMode.value = false
        _aiServerUrl.value = PreferencesManager.DEFAULT_AI_SERVER_URL
        _aiModel.value = PreferencesManager.DEFAULT_AI_MODEL
        _aiAutoTranscribe.value = false
        _aiLlmProvider.value = PreferencesManager.DEFAULT_AI_LLM_PROVIDER
        _isOnboardingCompleted.value = false
        _rawRecordings.value = emptyList()
        _selectedRecording.value = null
        _selectedTranscript.value = null
        _selectedMetadata.value = null
        _selectedBookmarks.value = emptyList()
        audioPlayer.stop()
    }

    fun selectRecording(recording: CallRecording?) {
        _selectedRecording.value = recording
        if (recording != null) {
            audioPlayer.play(
                recording.uri,
                recording.resolvedName,
                recording.resolvedSubtext ?: ""
            )
            loadSelectedTranscript()
            loadSelectedMetadata()
            loadSelectedBookmarks()

            if (_aiAutoTranscribe.value && recording.transcriptionStatus != "completed") {
                android.util.Log.d("MainViewModel", "Auto-transcribing selected recording: ${recording.displayName}")
                transcribeRecording(recording)
            }
        } else {
            audioPlayer.stop()
            _selectedTranscript.value = null
            _selectedMetadata.value = null
            _selectedBookmarks.value = emptyList()
        }
    }

    fun togglePlayPause() {
        if (isPlaying.value) {
            audioPlayer.pause()
        } else {
            _selectedRecording.value?.let {
                audioPlayer.play(
                    it.uri,
                    it.resolvedName,
                    it.resolvedSubtext ?: ""
                )
            }
        }
    }

    fun seekTo(position: Long) {
        audioPlayer.seekTo(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        audioPlayer.setPlaybackSpeed(speed)
    }

    fun skipForward() {
        val newPos = (currentPosition.value + 10000).coerceAtMost(duration.value)
        audioPlayer.seekTo(newPos)
    }

    fun skipBackward() {
        val newPos = (currentPosition.value - 10000).coerceAtLeast(0L)
        audioPlayer.seekTo(newPos)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDirectionFilter(direction: String) {
        _directionFilter.value = direction
    }

    fun setSimFilter(sim: Int?) {
        _simFilter.value = sim
    }

    fun deleteRecording(recording: CallRecording) {
        val folder = _folderUri.value ?: return
        viewModelScope.launch {
            val success = repository.deleteRecording(folder, recording)
            if (success) {
                if (_selectedRecording.value?.uri == recording.uri) {
                    selectRecording(null)
                }
                loadRecordings()
                loadRecycledFiles()
            }
        }
    }

    fun restoreRecycledFile(fileName: String) {
        val folder = _folderUri.value ?: return
        viewModelScope.launch {
            val success = repository.restoreRecording(folder, fileName)
            if (success) {
                loadRecordings()
                loadRecycledFiles()
            }
        }
    }

    fun deletePermanently(fileName: String) {
        viewModelScope.launch {
            val success = repository.deletePermanently(fileName)
            if (success) {
                loadRecycledFiles()
            }
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            val success = repository.emptyRecycleBin()
            if (success) {
                loadRecycledFiles()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
