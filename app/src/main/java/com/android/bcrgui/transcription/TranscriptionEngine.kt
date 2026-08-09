package com.android.bcrgui.transcription

import android.content.Context
import android.util.Log
import com.android.bcrgui.BuildConfig
import com.android.bcrgui.model.AiMetadata
import com.android.bcrgui.model.AiTranscription
import com.android.bcrgui.model.TranscriptionSegment
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

interface TranscriptionEngine {
    suspend fun transcribe(
        context: Context,
        audioUri: android.net.Uri,
        modelName: String,
        language: String? = null,
        additionalLanguages: String? = null,
        diarize: Boolean = false
    ): Result<AiTranscription>

    suspend fun generateMetadata(
        transcriptionText: String,
        language: String? = null,
        llmProvider: String = "none"
    ): Result<AiMetadata>

    suspend fun cancel()
}

class RemoteTranscriptionEngine(
    private val serverUrl: String,
    private val httpClient: okhttp3.OkHttpClient
) : TranscriptionEngine {

    override suspend fun transcribe(
        context: Context,
        audioUri: android.net.Uri,
        modelName: String,
        language: String?,
        additionalLanguages: String?,
        diarize: Boolean
    ): Result<AiTranscription> {
        return try {
            if (BuildConfig.DEBUG) Log.d(TAG, "RemoteTranscriptionEngine: url=${serverUrl.replace(Regex("\\d"), "X")}, model=$modelName, language=$language, diarize=$diarize")
            val stream = context.contentResolver.openInputStream(audioUri) ?: return Result.failure(Exception("Cannot open audio URI"))
            val bytes = stream.readBytes()

            if (BuildConfig.DEBUG) Log.d(TAG, "RemoteTranscriptionEngine: audio size=${bytes.size}")

            val audioPart = bytes.toRequestBody("application/octet-stream".toMediaType())

            val multipartBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", "audio.ogg", audioPart)
                .addFormDataPart("model", modelName)
                .apply {
                    if (!language.isNullOrBlank()) {
                        addFormDataPart("language", language)
                    }
                    if (!additionalLanguages.isNullOrBlank()) {
                        addFormDataPart("additional_languages", additionalLanguages)
                    }
                    if (diarize) {
                        addFormDataPart("diarize", "true")
                    }
                }
                .build()

            val request = okhttp3.Request.Builder()
                .url("$serverUrl/v1/transcribe")
                .post(multipartBody)
                .build()

            if (BuildConfig.DEBUG) Log.d(TAG, "RemoteTranscriptionEngine: sending request to ${request.url.toString().replace(Regex("\\d"), "X")}")
            val response = httpClient.newCall(request).execute()
            if (BuildConfig.DEBUG) Log.d(TAG, "RemoteTranscriptionEngine: response code=${response.code}, message=${response.message}")

            if (!response.isSuccessful) {
                return Result.failure(Exception("Server error: ${response.code} ${response.message}"))
            }

            val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            if (BuildConfig.DEBUG) Log.d(TAG, "RemoteTranscriptionEngine: response body_len=${responseBody.length}")
            val resultJson = org.json.JSONObject(responseBody)

            val segmentsArray = resultJson.optJSONArray("segments") ?: org.json.JSONArray()
            val segments = mutableListOf<TranscriptionSegment>()
            for (i in 0 until segmentsArray.length()) {
                val seg = segmentsArray.getJSONObject(i)
                val speaker = seg.optString("speaker", null)
                segments.add(
                    TranscriptionSegment(
                        startMs = seg.optLong("start_ms", 0L),
                        endMs = seg.optLong("end_ms", 0L),
                        text = seg.optString("text", ""),
                        speaker = if (!speaker.isNullOrBlank()) speaker else null
                    )
                )
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "RemoteTranscriptionEngine: parsed ${segments.size} segments")

            val transcription = AiTranscription(
                text = resultJson.optString("text", ""),
                language = resultJson.optString("language", null),
                model = modelName,
                segments = segments,
                durationMs = resultJson.optLong("duration_ms", 0L),
                generatedAt = System.currentTimeMillis()
            )
            if (BuildConfig.DEBUG) Log.d(TAG, "RemoteTranscriptionEngine: returning success")
            Result.success(transcription)
        } catch (e: Exception) {
            Log.e(TAG, "RemoteTranscriptionEngine: error", e)
            Result.failure(e)
        }
    }

    override suspend fun cancel() {
    }

    override suspend fun generateMetadata(
        transcriptionText: String,
        language: String?,
        llmProvider: String
    ): Result<AiMetadata> {
        return try {
            if (BuildConfig.DEBUG) Log.d(TAG, "RemoteTranscriptionEngine: requesting metadata from /v1/metadata")
            val requestJson = org.json.JSONObject().apply {
                put("transcript", transcriptionText)
                put("language", language ?: "en")
            }
            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = okhttp3.Request.Builder()
                .url("$serverUrl/v1/metadata")
                .post(requestBody)
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Metadata server error: ${response.code} ${response.message}"))
            }
            val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty metadata response"))
            if (BuildConfig.DEBUG) Log.d(TAG, "RemoteTranscriptionEngine: metadata response_len=${responseBody.length}")
            val resultJson = org.json.JSONObject(responseBody)
            val tagsArray = resultJson.optJSONArray("tags") ?: org.json.JSONArray()
            val tags = mutableListOf<String>()
            for (i in 0 until tagsArray.length()) {
                tags.add(tagsArray.optString(i, ""))
            }
            val metadata = AiMetadata(
                summary = resultJson.optString("summary", null),
                category = resultJson.optString("category", null),
                tags = tags,
                notes = resultJson.optString("notes", null),
                transcriptionRef = null,
                generatedAt = System.currentTimeMillis()
            )
            Result.success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "RemoteTranscriptionEngine: metadata error", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "RemoteTranscriptionEngine"
    }
}

class OnDeviceTranscriptionEngine : TranscriptionEngine {

    override suspend fun transcribe(
        context: Context,
        audioUri: android.net.Uri,
        modelName: String,
        language: String?,
        additionalLanguages: String?,
        diarize: Boolean
    ): Result<AiTranscription> {
        return Result.success(
            AiTranscription(
                text = "[On-device transcription placeholder. Integrate whisper.cpp or similar for actual on-device transcription.]",
                language = language ?: "en",
                model = "on-device-$modelName",
                segments = emptyList(),
                durationMs = 0L,
                generatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun generateMetadata(
        transcriptionText: String,
        language: String?,
        llmProvider: String
    ): Result<AiMetadata> {
        return Result.success(
            AiMetadata(
                summary = transcriptionText.take(200) + if (transcriptionText.length > 200) "..." else "",
                category = null,
                tags = emptyList(),
                notes = null,
                transcriptionRef = null,
                generatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun cancel() {
    }
}
