package com.android.bcrgui.transcription

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.android.bcrgui.BuildConfig
import com.android.bcrgui.model.AiTranscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = buildNotification("Preparing transcription...", 0, "preparing")
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val folderUriStr = inputData.getString(KEY_FOLDER_URI) ?: run {
            Log.e(TAG, "Missing KEY_FOLDER_URI")
            return@withContext Result.failure()
        }
        val baseName = inputData.getString(KEY_BASE_NAME) ?: run {
            Log.e(TAG, "Missing KEY_BASE_NAME")
            return@withContext Result.failure()
        }
        val audioUriStr = inputData.getString(KEY_AUDIO_URI) ?: run {
            Log.e(TAG, "Missing KEY_AUDIO_URI")
            return@withContext Result.failure()
        }
        val audioUri = android.net.Uri.parse(audioUriStr)
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: "default"
        val language = inputData.getString(KEY_LANGUAGE)
        val serverUrl = inputData.getString(KEY_SERVER_URL)
        val useRemote = inputData.getBoolean(KEY_USE_REMOTE, false)
        val diarize = inputData.getBoolean(KEY_DIARIZE, false)
        val llmProvider = inputData.getString(KEY_LLM_PROVIDER) ?: "none"

        if (BuildConfig.DEBUG) Log.d(TAG, "doWork: baseName=${redact(baseName)}, useRemote=$useRemote, serverUrl=${serverUrl.replace(Regex("\\d"), "X")}, audioUri=${audioUri.toString().replace(Regex("\\d"), "X")}")

        try {
            setForegroundAsync(buildForegroundInfo("Starting transcription...", 0, "starting"))

            val engine: TranscriptionEngine = if (useRemote && !serverUrl.isNullOrBlank()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Using RemoteTranscriptionEngine")
                RemoteTranscriptionEngine(serverUrl, okhttp3.OkHttpClient.Builder().build())
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Using OnDeviceTranscriptionEngine")
                OnDeviceTranscriptionEngine()
            }

            setForegroundAsync(buildForegroundInfo("Transcribing...", 25, "transcribing"))
            val transcriptionResult = engine.transcribe(applicationContext, audioUri, modelName, language, diarize)

            if (!transcriptionResult.isSuccess) {
                val error = transcriptionResult.exceptionOrNull()
                Log.e(TAG, "Transcription failed", error)
                setForegroundAsync(buildForegroundInfo("Transcription failed", 0, "failed"))
                return@withContext Result.failure()
            }

            val transcription = transcriptionResult.getOrNull()!!
            if (BuildConfig.DEBUG) Log.d(TAG, "Transcription success: text_length=${transcription.text.length}, segments=${transcription.segments.size}")
            setForegroundAsync(buildForegroundInfo("Saving transcript...", 60, "saving"))
            val transcriptRepo = TranscriptRepository(applicationContext)
            val transcriptSaved = transcriptRepo.saveTranscript(folderUriStr, baseName, transcription)
            if (BuildConfig.DEBUG) Log.d(TAG, "Transcript saved: $transcriptSaved")

            setForegroundAsync(buildForegroundInfo("Generating metadata...", 75, "generating_metadata"))
            val metadataResult = engine.generateMetadata(transcription.text, transcription.language, llmProvider)
            val metadata = metadataResult.getOrNull()
            if (metadata != null) {
                val metadataRepo = MetadataRepository(applicationContext)
                val metadataSaved = metadataRepo.saveMetadata(folderUriStr, baseName, metadata)
                if (BuildConfig.DEBUG) Log.d(TAG, "Metadata saved: $metadataSaved")
            } else {
                val metadataError = metadataResult.exceptionOrNull()
                Log.w(TAG, "Metadata generation failed, transcript already saved", metadataError)
            }

            setForegroundAsync(buildForegroundInfo("Completed", 100, "completed"))
            if (BuildConfig.DEBUG) Log.d(TAG, "Transcription worker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Transcription error", e)
            setForegroundAsync(buildForegroundInfo("Error: ${e.message}", 0, "error"))
            Result.failure()
        }
    }

    private fun buildForegroundInfo(message: String, progress: Int, phase: String): ForegroundInfo {
        val notification = buildNotification(message, progress, phase)
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(message: String, progress: Int, phase: String): Notification {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "Transcription Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for AI transcription jobs"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Transcription")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(progress < 100 && phase !in listOf("failed", "error", "completed"))
            .setProgress(100, progress, progress == 0)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "bcr_transcription_channel"
        const val NOTIFICATION_ID = 2027
        private const val TAG = "TranscriptionWorker"

        const val KEY_FOLDER_URI = "folder_uri"
        const val KEY_BASE_NAME = "base_name"
        const val KEY_AUDIO_URI = "audio_uri"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_LANGUAGE = "language"
        const val KEY_DIARIZE = "diarize"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USE_REMOTE = "use_remote"
        const val KEY_LLM_PROVIDER = "llm_provider"

        private fun redact(name: String): String = name.replace(Regex("\\d"), "X")
    }
}
