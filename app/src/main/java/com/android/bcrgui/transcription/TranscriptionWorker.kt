package com.android.bcrgui.transcription

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.android.bcrgui.model.AiMetadata
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
        val folderUriStr = inputData.getString(KEY_FOLDER_URI) ?: return@withContext Result.failure()
        val baseName = inputData.getString(KEY_BASE_NAME) ?: return@withContext Result.failure()
        val audioUriStr = inputData.getString(KEY_AUDIO_URI) ?: return@withContext Result.failure()
        val audioUri = android.net.Uri.parse(audioUriStr)
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: "default"
        val language = inputData.getString(KEY_LANGUAGE)
        val serverUrl = inputData.getString(KEY_SERVER_URL)
        val useRemote = inputData.getBoolean(KEY_USE_REMOTE, false)

        try {
            setForegroundAsync(buildForegroundInfo("Starting transcription...", 0, "starting"))

            val engine: TranscriptionEngine = if (useRemote && !serverUrl.isNullOrBlank()) {
                RemoteTranscriptionEngine(serverUrl, okhttp3.OkHttpClient.Builder().build())
            } else {
                OnDeviceTranscriptionEngine()
            }

            setForegroundAsync(buildForegroundInfo("Transcribing...", 25, "transcribing"))
            val transcriptionResult = engine.transcribe(applicationContext, audioUri, modelName, language)

            if (!transcriptionResult.isSuccess) {
                setForegroundAsync(buildForegroundInfo("Transcription failed", 0, "failed"))
                return@withContext Result.failure()
            }

            val transcription = transcriptionResult.getOrNull()!!
            setForegroundAsync(buildForegroundInfo("Saving transcript...", 60, "saving"))

            val transcriptRepo = TranscriptRepository(applicationContext)
            transcriptRepo.saveTranscript(folderUriStr, baseName, transcription)

            val metadataRepo = MetadataRepository(applicationContext)
            val metadata = generateMetadata(transcription)
            metadataRepo.saveMetadata(folderUriStr, baseName, metadata)

            setForegroundAsync(buildForegroundInfo("Completed", 100, "completed"))
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            setForegroundAsync(buildForegroundInfo("Error: ${e.message}", 0, "error"))
            Result.failure()
        }
    }

    private fun generateMetadata(transcription: AiTranscription): AiMetadata {
        return AiMetadata(
            summary = transcription.text.take(200) + if (transcription.text.length > 200) "..." else "",
            category = null,
            tags = emptyList(),
            notes = null,
            transcriptionRef = null,
            generatedAt = System.currentTimeMillis()
        )
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

        const val KEY_FOLDER_URI = "folder_uri"
        const val KEY_BASE_NAME = "base_name"
        const val KEY_AUDIO_URI = "audio_uri"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_LANGUAGE = "language"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USE_REMOTE = "use_remote"
    }
}
