package com.android.bcrgui.transcription

import android.content.Context
import android.util.Log
import com.android.bcrgui.model.AiTranscription
import com.android.bcrgui.model.TranscriptionSegment
import okhttp3.MediaType.Companion.toMediaType

interface TranscriptionEngine {
    suspend fun transcribe(
        context: Context,
        audioUri: android.net.Uri,
        modelName: String,
        language: String? = null
    ): Result<AiTranscription>

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
        language: String?
    ): Result<AiTranscription> {
        return try {
            Log.d(TAG, "RemoteTranscriptionEngine: url=$serverUrl, model=$modelName, language=$language")
            val stream = context.contentResolver.openInputStream(audioUri) ?: return Result.failure(Exception("Cannot open audio URI"))
            val bytes = stream.readBytes()
            val base64Audio = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

            Log.d(TAG, "RemoteTranscriptionEngine: audio size=${bytes.size}, base64 size=${base64Audio.length}")

            val json = org.json.JSONObject().apply {
                put("model", modelName)
                put("audio_base64", base64Audio)
                if (!language.isNullOrBlank()) put("language", language)
            }

            val requestBody = okhttp3.RequestBody.create(
                "application/json".toMediaType(),
                json.toString()
            )

            val request = okhttp3.Request.Builder()
                .url("$serverUrl/v1/transcribe")
                .post(requestBody)
                .build()

            Log.d(TAG, "RemoteTranscriptionEngine: sending request to ${request.url}")
            val response = httpClient.newCall(request).execute()
            Log.d(TAG, "RemoteTranscriptionEngine: response code=${response.code}, message=${response.message}")

            if (!response.isSuccessful) {
                return Result.failure(Exception("Server error: ${response.code} ${response.message}"))
            }

            val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val resultJson = org.json.JSONObject(responseBody)

            val segmentsArray = resultJson.optJSONArray("segments") ?: org.json.JSONArray()
            val segments = mutableListOf<TranscriptionSegment>()
            for (i in 0 until segmentsArray.length()) {
                val seg = segmentsArray.getJSONObject(i)
                segments.add(
                    TranscriptionSegment(
                        startMs = seg.optLong("start_ms", 0L),
                        endMs = seg.optLong("end_ms", 0L),
                        text = seg.optString("text", "")
                    )
                )
            }

            val transcription = AiTranscription(
                text = resultJson.optString("text", ""),
                language = resultJson.optString("language", null),
                model = modelName,
                segments = segments,
                durationMs = resultJson.optLong("duration_ms", 0L),
                generatedAt = System.currentTimeMillis()
            )
            Result.success(transcription)
        } catch (e: Exception) {
            Log.e(TAG, "RemoteTranscriptionEngine: error", e)
            Result.failure(e)
        }
    }

    override suspend fun cancel() {
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
        language: String?
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

    override suspend fun cancel() {
    }
}
