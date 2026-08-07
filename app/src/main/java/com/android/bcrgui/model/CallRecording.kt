package com.android.bcrgui.model

import android.net.Uri

data class CallRecording(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val date: String?,
    val direction: String?,
    val simSlot: Int?,
    val phoneNumber: String?,
    val contactName: String?,
    val callerName: String?,
    val callLogName: String?,
    val durationMs: Long,
    val hasMetadataJson: Boolean,
    val packageName: String? = null,
    val transcriptionStatus: String? = null,
    val metadataStatus: String? = null,
    val bookmarkCount: Int = 0
) {
    /**
     * Resolves the primary name to display. 
     * Priority: Contact Name -> Call Log Name -> Caller Name -> Phone Number -> "Private Number/Unknown"
     */
    val resolvedName: String
        get() = when {
            !contactName.isNullOrBlank() -> contactName
            !callLogName.isNullOrBlank() -> callLogName
            !callerName.isNullOrBlank() -> callerName
            !phoneNumber.isNullOrBlank() -> phoneNumber
            else -> "Unknown/Private"
        }

    /**
     * Resolves subtext to display below the primary name (e.g. phone number if name was shown).
     */
    val resolvedSubtext: String?
        get() = when {
            !contactName.isNullOrBlank() || !callLogName.isNullOrBlank() || !callerName.isNullOrBlank() -> phoneNumber
            else -> null
        }

    /**
     * Formats the raw call date string into a user-friendly format showing local date and time.
     */
    val formattedDateTime: String
        get() {
            if (date != null) {
                try {
                    val parser = java.text.SimpleDateFormat("yyyyMMdd_HHmmss.SSSZ", java.util.Locale.getDefault())
                    val d = parser.parse(date)
                    if (d != null) {
                        val formatter = java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.getDefault())
                        return formatter.format(d)
                    }
                } catch (e: Exception) {
                    try {
                        if (date.all { it.isDigit() }) {
                            val ms = date.toLong()
                            val formatter = java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.getDefault())
                            return formatter.format(java.util.Date(ms))
                        }
                    } catch (ex: Exception) {}
                }
            }
            val formatter = java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.getDefault())
            return formatter.format(java.util.Date(lastModified))
        }
}

data class RecycledFile(
    val name: String,
    val size: Long,
    val lastModified: Long,
    val contactName: String? = null,
    val phoneNumber: String? = null
) {
    val resolvedName: String
        get() = when {
            !contactName.isNullOrBlank() -> contactName
            !phoneNumber.isNullOrBlank() -> phoneNumber
            else -> name
        }

    val resolvedSubtext: String?
        get() = if (!contactName.isNullOrBlank()) {
            if (!phoneNumber.isNullOrBlank()) "$phoneNumber • $name" else name
        } else {
            null
        }

    val formattedDateTime: String
        get() {
            val formatter = java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.getDefault())
            return formatter.format(java.util.Date(lastModified))
        }
}

data class Bookmark(
    val timestampMs: Long,
    val label: String,
    val color: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class TranscriptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class AiTranscription(
    val text: String,
    val language: String? = null,
    val model: String,
    val segments: List<TranscriptionSegment> = emptyList(),
    val durationMs: Long = 0L,
    val generatedAt: Long = System.currentTimeMillis()
)

data class AiMetadata(
    val summary: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val transcriptionRef: String? = null,
    val generatedAt: Long = System.currentTimeMillis()
)
