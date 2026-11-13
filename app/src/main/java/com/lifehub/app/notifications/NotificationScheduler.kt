package com.lifehub.app.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    const val UNIQUE_NAME = "daily-check"
    const val STREAK_NAME = "streak-check"

    /** Fixed evening slot of the streak reminder (late enough that "no workout today" is meaningful). */
    val STREAK_TIME: LocalTime = LocalTime.of(20, 0)

    fun delayUntilNext(now: ZonedDateTime, time: LocalTime) = NotificationRules.delayUntilNext(now, time)

    /**
     * KEEP on app start; CANCEL_AND_REENQUEUE when the check time changes (replace = true). Also (re)schedules the
     * streak check at [STREAK_TIME].
     */
    fun schedule(context: Context, time: LocalTime, replace: Boolean) {
        enqueue(context, UNIQUE_NAME, DailyCheckWorker::class.java, time, replace)
        enqueue(context, STREAK_NAME, StreakCheckWorker::class.java, STREAK_TIME, replace = false)
    }

    private fun enqueue(context: Context, name: String, worker: Class<out ListenableWorker>, time: LocalTime, replace: Boolean) {
        try {
            val delay = delayUntilNext(ZonedDateTime.now(), time)
            val req = PeriodicWorkRequest.Builder(worker, 24, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                name,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        } catch (_: Exception) {
            // WorkManager unavailable (e.g. not initialised in unit tests); reminders simply stay off.
        }
    }

    fun cancel(context: Context) {
        try {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.cancelUniqueWork(UNIQUE_NAME); wm.cancelUniqueWork(STREAK_NAME)
        } catch (_: Exception) {}
    }
}
