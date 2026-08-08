package com.android.bcrgui.transcription

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.android.bcrgui.BuildConfig
import com.android.bcrgui.model.AiMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MetadataRepository(private val context: Context) {

    companion object {
        private const val TAG = "MetadataRepository"
    }

    suspend fun getMetadata(folderUriStr: String, baseName: String): AiMetadata? = withContext(Dispatchers.IO) {
        if (folderUriStr.isEmpty()) return@withContext null
        val treeUri = Uri.parse(folderUriStr)
        val jsonName = "$baseName.metadata.json"
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
                        return@withContext parseMetadataJson(jsonText)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun saveMetadata(folderUriStr: String, baseName: String, metadata: AiMetadata): Boolean = withContext(Dispatchers.IO) {
        if (folderUriStr.isEmpty()) return@withContext false
        val treeUri = Uri.parse(folderUriStr)
        val jsonName = "$baseName.metadata.json"
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

        val json = JSONObject().apply {
            put("summary", metadata.summary)
            put("category", metadata.category)
            put("notes", metadata.notes)
            put("transcription_ref", metadata.transcriptionRef)
            put("generated_at", metadata.generatedAt)
            val tagsArray = JSONArray(metadata.tags)
            put("tags", tagsArray)
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
                if (BuildConfig.DEBUG) Log.d(TAG, "Saved metadata to ${docUri.toString().replace(Regex("\\d"), "X")}")
                true
            } else {
                Log.w(TAG, "Failed to create document for metadata")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving metadata", e)
            false
        }
    }

    private fun findDocumentId(folderUriStr: String, targetName: String): String? {
        if (folderUriStr.isEmpty()) return null
        val treeUri = Uri.parse(folderUriStr)
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

    private fun parseMetadataJson(jsonText: String): AiMetadata {
        return try {
            val json = JSONObject(jsonText)
            val tagsArray = json.optJSONArray("tags") ?: JSONArray()
            val tags = mutableListOf<String>()
            for (i in 0 until tagsArray.length()) {
                tags.add(tagsArray.optString(i, ""))
            }
            AiMetadata(
                summary = json.optString("summary", null),
                category = json.optString("category", null),
                tags = tags,
                notes = json.optString("notes", null),
                transcriptionRef = json.optString("transcription_ref", null),
                generatedAt = json.optLong("generated_at", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            AiMetadata()
        }
    }
}
