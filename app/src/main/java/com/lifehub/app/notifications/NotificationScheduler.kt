package com.lifehub.app.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    const val UNIQUE_NAME = "daily-check"

    fun delayUntilNext(now: ZonedDateTime, time: LocalTime) = NotificationRules.delayUntilNext(now, time)

    /** KEEP on app start; CANCEL_AND_REENQUEUE when the check time changes (replace = true). */
    fun schedule(context: Context, time: LocalTime, replace: Boolean) {
        try {
            val delay = delayUntilNext(ZonedDateTime.now(), time)
            val req = PeriodicWorkRequestBuilder<DailyCheckWorker>(24, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        } catch (_: Exception) {
            // WorkManager unavailable (e.g. not initialised in unit tests); reminders simply stay off.
        }
    }

    fun cancel(context: Context) {
        try { WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME) } catch (_: Exception) {}
    }
}
