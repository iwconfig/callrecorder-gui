package com.android.bcrgui.model

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookmarkRepository(private val context: Context) {

    suspend fun getBookmarks(folderUriStr: String, baseName: String): List<Bookmark> = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(folderUriStr) ?: return@withContext emptyList()
        val jsonName = "$baseName.bookmarks.json"
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
                    val curName = cursor.getString(nameCol)
                    if (curName == jsonName) {
                        val docId = cursor.getString(idCol)
                        val jsonUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val jsonText = context.contentResolver.openInputStream(jsonUri)?.use { stream ->
                            stream.bufferedReader().use { it.readText() }
                        } ?: return@withContext emptyList()

                        return@withContext parseBookmarksJson(jsonText)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext emptyList()
    }

    suspend fun saveBookmarks(folderUriStr: String, baseName: String, bookmarks: List<Bookmark>): Boolean = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(folderUriStr) ?: return@withContext false
        val jsonName = "$baseName.bookmarks.json"
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

        val jsonArray = JSONArray()
        bookmarks.forEach { bm ->
            val obj = JSONObject().apply {
                put("timestamp_ms", bm.timestampMs)
                put("label", bm.label)
                put("color", bm.color)
                put("created_at", bm.createdAt)
            }
            jsonArray.put(obj)
        }

        val jsonText = jsonArray.toString()

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
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteBookmarks(folderUriStr: String, baseName: String): Boolean = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(folderUriStr) ?: return@withContext false
        val jsonName = "$baseName.bookmarks.json"
        val docId = findDocumentId(folderUriStr, jsonName) ?: return@withContext true

        return@withContext try {
            val jsonUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            DocumentsContract.deleteDocument(context.contentResolver, jsonUri)
        } catch (e: Exception) {
            e.printStackTrace()
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

    private fun parseBookmarksJson(jsonText: String): List<Bookmark> {
        return try {
            val jsonArray = JSONArray(jsonText)
            val bookmarks = mutableListOf<Bookmark>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                bookmarks.add(
                    Bookmark(
                        timestampMs = obj.optLong("timestamp_ms", 0L),
                        label = obj.optString("label", ""),
                        color = obj.optString("color", null),
                        createdAt = obj.optLong("created_at", System.currentTimeMillis())
                    )
                )
            }
            bookmarks
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
