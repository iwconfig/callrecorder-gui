package com.android.bcrgui.transcription

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.android.bcrgui.model.AiTranscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class TranscriptRepository(private val context: Context) {

    companion object {
        private const val TAG = "TranscriptRepository"
    }

    suspend fun getTranscript(folderUriStr: String, baseName: String): AiTranscription? = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(folderUriStr) ?: return@withContext null
        val jsonName = "$baseName.transcript.json"
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameCol) == jsonName) {
                        val docId = cursor.getString(idCol)
                        val jsonUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val jsonText = context.contentResolver.openInputStream(jsonUri)?.use { stream ->
                            stream.bufferedReader().use { it.readText() }
                        } ?: return@withContext null
                        return@withContext parseTranscriptJson(jsonText)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun saveTranscript(folderUriStr: String, baseName: String, transcription: AiTranscription): Boolean = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(folderUriStr) ?: return@withContext false
        val jsonName = "$baseName.transcript.json"
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

        val json = JSONObject().apply {
            put("text", transcription.text)
            put("language", transcription.language)
            put("model", transcription.model)
            put("duration_ms", transcription.durationMs)
            put("generated_at", transcription.generatedAt)
            val segmentsArray = org.json.JSONArray()
            transcription.segments.forEach { seg ->
                segmentsArray.put(JSONObject().apply {
                    put("start_ms", seg.startMs)
                    put("end_ms", seg.endMs)
                    put("text", seg.text)
                })
            }
            put("segments", segmentsArray)
        }

        val jsonText = json.toString()

        return@withContext try {
            val existingDocId = findDocumentId(folderUriStr, jsonName)
            val docUri = if (existingDocId != null) {
                DocumentsContract.buildDocumentUriUsingTree(treeUri, existingDocId)
            } else {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocumentUri,
                    "application/json",
                    jsonName
                )
            }

            if (docUri != null) {
                context.contentResolver.openOutputStream(docUri)?.use { output ->
                    output.write(jsonText.toByteArray(Charsets.UTF_8))
                }
                Log.d(TAG, "Saved transcript to $docUri")
                true
            } else {
                Log.w(TAG, "Failed to create document for transcript")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving transcript", e)
            false
        }
    }

    private fun findDocumentId(folderUriStr: String, targetName: String): String? {
        val treeUri = Uri.parse(folderUriStr) ?: return null
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )

        return try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameCol) == targetName) {
                        return cursor.getString(idCol)
                    }
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseTranscriptJson(jsonText: String): AiTranscription? {
        return try {
            val json = JSONObject(jsonText)
            val segmentsArray = json.optJSONArray("segments") ?: org.json.JSONArray()
            val segments = mutableListOf<com.android.bcrgui.model.TranscriptionSegment>()
            for (i in 0 until segmentsArray.length()) {
                val seg = segmentsArray.getJSONObject(i)
                segments.add(
                    com.android.bcrgui.model.TranscriptionSegment(
                        startMs = seg.optLong("start_ms", 0L),
                        endMs = seg.optLong("end_ms", 0L),
                        text = seg.optString("text", "")
                    )
                )
            }
            AiTranscription(
                text = json.optString("text", ""),
                language = json.optString("language", null),
                model = json.optString("model", ""),
                segments = segments,
                durationMs = json.optLong("duration_ms", 0L),
                generatedAt = json.optLong("generated_at", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
