package com.lifehub.app.copilot.capture

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lifehub.app.R
import com.lifehub.app.container
import com.lifehub.app.copilot.notify.CopilotChannel
import com.lifehub.app.notifications.Channels
import kotlinx.coroutines.CancellationException

/**
 * Thin WorkManager wrapper around [CaptureRunner] (design doc §11): runs one capture without the app open. WorkManager
 * persists the request, so it survives process death and reboot; an interrupted run is simply run again and resumes from
 * the missing steps. No automatic retry: a failure leaves the capture FAILED with an Error notification and Retry.
 */
class CaptureWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_CAPTURE_ID) ?: return Result.failure()
        val runner = applicationContext.container.captureRunner
        return try {
            runner.run(id, notify = true)
            Result.success()
        } catch (e: CancellationException) {
            throw e   // stopped by the system: WorkManager runs it again later, the capture is still RECORDED/PROCESSING
        } catch (_: Exception) {
            runCatching { runner.failUnexpected(id) }
            Result.success()
        }
    }

    /** Only used before Android 12, where WorkManager runs expedited work through its own foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        Channels.ensure(applicationContext)
        val n = NotificationCompat.Builder(applicationContext, CopilotChannel.OUTCOME.id)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Processing your voice note…")
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(FOREGROUND_ID, n)
    }

    companion object {
        const val KEY_CAPTURE_ID = "captureId"
        private const val FOREGROUND_ID = 7002
        fun uniqueName(captureId: String) = "copilot-capture-$captureId"
    }
}

/**
 * Expedited one-time work per capture, falling back to normal work when the expedited quota is used up
 * (RUN_AS_NON_EXPEDITED_WORK_REQUEST, never DROP). Unique per capture; APPEND_OR_REPLACE chains a second request (e.g. an
 * answer arriving while the first run is still going) after the running one instead of dropping it; a run with nothing
 * to do is a no-op.
 */
class WorkManagerCaptureScheduler(context: Context) : CaptureScheduler {
    private val ctx = context.applicationContext
    override fun enqueue(captureId: String) {
        val req = OneTimeWorkRequestBuilder<CaptureWorker>()
            .setInputData(workDataOf(CaptureWorker.KEY_CAPTURE_ID to captureId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(CaptureWorker.uniqueName(captureId), ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    companion object { const val TAG = "copilot-capture" }
}
