package com.android.bcrgui.model

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.android.bcrgui.parser.BcrTemplateParser
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class RecordingRepository(private val context: Context) {
    private val bookmarkRepository = BookmarkRepository(context)

    /**
     * Scans the given SAF Tree Uri and returns a parsed list of call recordings.
     */
    suspend fun getRecordings(
        folderUriStr: String,
        template: String,
        extension: String
    ): List<CallRecording> = withContext(Dispatchers.IO) {
        if (folderUriStr.isEmpty()) return@withContext emptyList()
        val treeUri = Uri.parse(folderUriStr)
        val recordings = mutableListOf<CallRecording>()

        val childrenUri = try {
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }

        val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "flv", "webm", "wmv", "mpg", "mpeg", "m4v", "3g2")
        val isAllExt = extension.lowercase() == "all" || extension.isBlank()
        val templateParser = BcrTemplateParser(template, if (isAllExt) "all" else extension)
        val audioExtension = if (isAllExt) "" else (if (extension.startsWith(".")) extension.lowercase() else ".$extension".lowercase())

        // Map to hold audio documents and metadata JSON documents
        val audioFiles = mutableListOf<DocInfo>()
        val jsonFiles = mutableMapOf<String, Uri>()
        val bookmarkFiles = mutableMapOf<String, Uri>()
        val transcriptFiles = mutableMapOf<String, Uri>()
        val metadataFiles = mutableMapOf<String, Uri>()

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val lastModified = cursor.getLong(dateCol)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    val dotIndex = name.lastIndexOf('.')
                    val ext = if (dotIndex != -1) name.substring(dotIndex + 1).lowercase() else ""

                    when {
                        name.lowercase().endsWith(".bookmarks.json") -> {
                            val baseName = name.substring(0, name.length - 14)
                            bookmarkFiles[baseName] = docUri
                        }
                        name.lowercase().endsWith(".transcript.json") -> {
                            val baseName = name.substring(0, name.length - 15)
                            transcriptFiles[baseName] = docUri
                        }
                        name.lowercase().endsWith(".metadata.json") -> {
                            val baseName = name.substring(0, name.length - 14)
                            metadataFiles[baseName] = docUri
                        }
                        name.lowercase().endsWith(".json") -> {
                            val baseName = name.substring(0, name.length - 5)
                            jsonFiles[baseName] = docUri
                        }
                        isAllExt -> {
                            if (ext.isNotEmpty() && !videoExtensions.contains(ext)) {
                                audioFiles.add(DocInfo(docUri, name, size, lastModified))
                            }
                        }
                        else -> {
                            if (name.lowercase().endsWith(audioExtension)) {
                                audioFiles.add(DocInfo(docUri, name, size, lastModified))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Process each audio file
        for (audioDoc in audioFiles) {
            val dotIndex = audioDoc.name.lastIndexOf('.')
            val baseName = if (dotIndex != -1) audioDoc.name.substring(0, dotIndex) else audioDoc.name
            val jsonUri = jsonFiles[baseName]
            var recording: CallRecording? = null

            if (jsonUri != null) {
                recording = parseJsonMetadata(audioDoc, jsonUri)
            }

            if (recording == null) {
                recording = parseFilenameMetadata(audioDoc, templateParser)
            }

            if (recording != null) {
                val bookmarkCount = bookmarkRepository.getBookmarks(folderUriStr, baseName).size
                val hasTranscript = transcriptFiles.containsKey(baseName)
                val hasMetadata = metadataFiles.containsKey(baseName)
                val transcriptionStatus = if (hasTranscript) "completed" else null
                val metadataStatus = if (hasMetadata) "completed" else null
                recordings.add(
                    recording.copy(
                        bookmarkCount = bookmarkCount,
                        transcriptionStatus = transcriptionStatus,
                        metadataStatus = metadataStatus
                    )
                )
            }
        }

        // Sort by date/lastModified descending by default (newest first)
        recordings.sortByDescending { it.lastModified }
        return@withContext recordings
    }

    private fun parseJsonMetadata(audioDoc: DocInfo, jsonUri: Uri): CallRecording? {
        return try {
            val jsonText = context.contentResolver.openInputStream(jsonUri)?.use { stream ->
                stream.bufferedReader().use { it.readText() }
            } ?: return null

            val json = JSONObject(jsonText)
            val timestamp = json.optString("timestamp", null)
            val timestampUnixMs = json.optLong("timestamp_unix_ms", audioDoc.lastModified)
            val direction = json.optString("direction", null)
            val packageName = json.optString("package_name", null)
            
            val simSlotVal = json.opt("sim_slot")
            val simSlot = if (simSlotVal is Int) simSlotVal else null
            val callLogName = json.optString("call_log_name", null)

            var phoneNumber: String? = null
            var callerName: String? = null
            var contactName: String? = null

            val callsArray = json.optJSONArray("calls")
            if (callsArray != null && callsArray.length() > 0) {
                val callObj = callsArray.getJSONObject(0)
                phoneNumber = callObj.optString("phone_number_formatted", null) ?: callObj.optString("phone_number", null)
                callerName = callObj.optString("caller_name", null)
                contactName = callObj.optString("contact_name", null)
            }

            // Read duration from json output.recording
            var durationMs = 0L
            val outputObj = json.optJSONObject("output")
            val recordingObj = outputObj?.optJSONObject("recording")
            if (recordingObj != null) {
                val durationSecs = recordingObj.optDouble("duration_secs_encoded", 0.0)
                if (durationSecs > 0.0) {
                    durationMs = (durationSecs * 1000).toLong()
                } else {
                    val durationSecsTotal = recordingObj.optDouble("duration_secs_total", 0.0)
                    durationMs = (durationSecsTotal * 1000).toLong()
                }
            }

            // Fallback for duration using metadata retriever if not in json
            if (durationMs <= 0L) {
                durationMs = retrieveDuration(audioDoc.uri)
            }

            CallRecording(
                uri = audioDoc.uri,
                displayName = audioDoc.name,
                size = audioDoc.size,
                lastModified = timestampUnixMs,
                date = timestamp ?: formatTimestamp(timestampUnixMs),
                direction = direction,
                simSlot = simSlot,
                phoneNumber = phoneNumber,
                contactName = contactName,
                callerName = callerName,
                callLogName = callLogName,
                durationMs = durationMs,
                hasMetadataJson = true,
                packageName = packageName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseFilenameMetadata(audioDoc: DocInfo, parser: BcrTemplateParser): CallRecording {
        val meta = parser.parseFilename(audioDoc.name)
        val durationMs = retrieveDuration(audioDoc.uri)

        return CallRecording(
            uri = audioDoc.uri,
            displayName = audioDoc.name,
            size = audioDoc.size,
            lastModified = audioDoc.lastModified,
            date = meta.date ?: formatTimestamp(audioDoc.lastModified),
            direction = meta.direction,
            simSlot = meta.simSlot,
            phoneNumber = meta.phoneNumber,
            contactName = meta.contactName,
            callerName = meta.callerName,
            callLogName = meta.callLogName,
            durationMs = durationMs,
            hasMetadataJson = false
        )
    }

    private fun retrieveDuration(uri: Uri): Long {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationStr?.toLongOrNull() ?: 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
               e.printStackTrace()
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun deleteRecording(folderUriStr: String, recording: CallRecording): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val recycleBinDir = File(context.filesDir, "recycle_bin")
            if (!recycleBinDir.exists()) {
                recycleBinDir.mkdirs()
            }
            
            // 1. Copy audio file to private recycle bin dir
            val destFile = File(recycleBinDir, recording.displayName)
            context.contentResolver.openInputStream(recording.uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // 2. Delete original SAF document
            DocumentsContract.deleteDocument(context.contentResolver, recording.uri)
            
            // 3. Try to copy and delete companion sidecar files
            val dotIndex = recording.displayName.lastIndexOf('.')
            val baseName = if (dotIndex != -1) recording.displayName.substring(0, dotIndex) else recording.displayName
            val sidecarNames = listOf(
                "$baseName.json",
                "$baseName.bookmarks.json",
                "$baseName.transcript.json",
                "$baseName.metadata.json"
            )

            val treeUri = Uri.parse(folderUriStr)
            if (treeUri != null) {
                val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)

                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                )
                try {
                    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            val curName = cursor.getString(nameCol)
                            if (sidecarNames.contains(curName)) {
                                val docId = cursor.getString(idCol)
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                                val destFile = File(recycleBinDir, curName)
                                context.contentResolver.openInputStream(docUri)?.use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                DocumentsContract.deleteDocument(context.contentResolver, docUri)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreRecording(folderUriStr: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val recycleBinDir = File(context.filesDir, "recycle_bin")
            val srcFile = File(recycleBinDir, fileName)
            if (!srcFile.exists()) return@withContext false

            if (folderUriStr.isEmpty()) return@withContext false
            val treeUri = Uri.parse(folderUriStr)
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

            val ext = srcFile.extension.lowercase()
            val mimeType = when (ext) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "wav" -> "audio/x-wav"
                "ogg", "oga" -> "audio/ogg"
                "3gp" -> "audio/3gpp"
                else -> "audio/*"
            }

            val destUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocumentUri,
                mimeType,
                fileName
            ) ?: return@withContext false

            context.contentResolver.openOutputStream(destUri)?.use { output ->
                srcFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            srcFile.delete()

            // Restore companion sidecar files if they exist
            val dotIndex = fileName.lastIndexOf('.')
            val baseName = if (dotIndex != -1) fileName.substring(0, dotIndex) else fileName
            val sidecarNames = listOf(
                "$baseName.json",
                "$baseName.bookmarks.json",
                "$baseName.transcript.json",
                "$baseName.metadata.json"
            )

            sidecarNames.forEach { sidecarName ->
                val srcSidecar = File(recycleBinDir, sidecarName)
                if (srcSidecar.exists()) {
                    val destSidecarUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentDocumentUri,
                        "application/json",
                        sidecarName
                    )
                    if (destSidecarUri != null) {
                        context.contentResolver.openOutputStream(destSidecarUri)?.use { output ->
                            srcSidecar.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                    srcSidecar.delete()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getRecycledRecordings(template: String, contactsMap: Map<String, String>): List<RecycledFile> {
        val recycleBinDir = File(context.filesDir, "recycle_bin")
        if (!recycleBinDir.exists()) return emptyList()

        val templateParser = BcrTemplateParser(template, "all")

        return recycleBinDir.listFiles { _, name -> !name.endsWith(".json") }?.map { file ->
            var contactName: String? = null
            var phoneNumber: String? = null

            val dotIndex = file.name.lastIndexOf('.')
            val baseName = if (dotIndex != -1) file.name.substring(0, dotIndex) else file.name
            val jsonFile = File(recycleBinDir, "$baseName.json")
            if (jsonFile.exists()) {
                try {
                    val jsonText = jsonFile.readText()
                    val json = JSONObject(jsonText)
                    val callsArray = json.optJSONArray("calls")
                    if (callsArray != null && callsArray.length() > 0) {
                        val callObj = callsArray.getJSONObject(0)
                        phoneNumber = callObj.optString("phone_number_formatted", null) ?: callObj.optString("phone_number", null)
                        val candidateName = callObj.optString("contact_name", null) ?: callObj.optString("call_log_name", null) ?: callObj.optString("caller_name", null)
                        if (!candidateName.isNullOrBlank()) {
                            contactName = candidateName
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (contactName.isNullOrBlank() && phoneNumber.isNullOrBlank()) {
                try {
                    val meta = templateParser.parseFilename(file.name)
                    contactName = meta.contactName ?: meta.callLogName ?: meta.callerName
                    phoneNumber = meta.phoneNumber
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (contactName.isNullOrBlank() && !phoneNumber.isNullOrBlank()) {
                val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(phoneNumber)
                val systemName = contactsMap[normalized] ?: contactsMap[phoneNumber]
                if (systemName != null) {
                    contactName = systemName
                }
            }

            RecycledFile(
                name = file.name,
                size = file.length(),
                lastModified = file.lastModified(),
                contactName = contactName,
                phoneNumber = phoneNumber
            )
        } ?: emptyList()
    }

    fun emptyRecycleBin(): Boolean {
        val recycleBinDir = File(context.filesDir, "recycle_bin")
        if (!recycleBinDir.exists()) return true
        return recycleBinDir.deleteRecursively()
    }

    fun deletePermanently(fileName: String): Boolean {
        val recycleBinDir = File(context.filesDir, "recycle_bin")
        val audioFile = File(recycleBinDir, fileName)
        var success = true
        if (audioFile.exists()) {
            success = audioFile.delete()
        }
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) fileName.substring(0, dotIndex) else fileName
        val sidecarNames = listOf(
            "$baseName.json",
            "$baseName.bookmarks.json",
            "$baseName.transcript.json",
            "$baseName.metadata.json"
        )
        sidecarNames.forEach { sidecarName ->
            val sidecarFile = File(recycleBinDir, sidecarName)
            if (sidecarFile.exists()) {
                success = sidecarFile.delete() && success
            }
        }
        return success
    }

    private data class DocInfo(
        val uri: Uri,
        val name: String,
        val size: Long,
        val lastModified: Long
    )
}
