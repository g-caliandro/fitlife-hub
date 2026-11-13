package com.lifehub.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lifehub.app.R
import com.lifehub.app.navigation.DeepLinks
import com.lifehub.app.navigation.Routes

/** Set while the live workout screen is in the foreground (the in-app alert handles the rest end then). */
object LiveWorkoutVisible {
    @Volatile var value: Boolean = false
}

/** Background rest-over alert: an alarm at `restEndsAt` that posts a heads-up notification (no foreground service). */
object RestAlarm {
    const val EXTRA_NEXT = "next"
    private const val REQUEST = 901
    private const val NOTIF_ID = 190

    private fun pending(ctx: Context, next: String?, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(ctx, REQUEST, Intent(ctx, RestAlarmReceiver::class.java).putExtra(EXTRA_NEXT, next), flags or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context, endsAtMillis: Long, nextExercise: String?) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = pending(ctx, nextExercise, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            if (useExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pi)
        } catch (_: Exception) {}
    }

    internal fun useExact(am: AlarmManager) = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    fun cancel(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            pending(ctx, null, PendingIntent.FLAG_UPDATE_CURRENT)?.let { am.cancel(it); it.cancel() }
            NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
        } catch (_: Exception) {}
    }

    @Suppress("MissingPermission")
    fun post(ctx: Context, next: String?) {
        if (!Notifier.hasPermission(ctx)) return
        Channels.ensure(ctx)
        val text = if (next.isNullOrBlank()) "Rest over" else "Rest over — next: $next"
        val n = NotificationCompat.Builder(ctx, Channels.REST_DONE)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(DeepLinks.pendingIntent(ctx, 900, Routes.WORKOUT_LIVE))
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val p = longArrayOf(0, 200, 120, 200)
            if (Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createWaveform(p, -1))
            else @Suppress("DEPRECATION") v?.vibrate(p, -1)
        } catch (_: Exception) {}
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (LiveWorkoutVisible.value) return
        RestAlarm.post(context, intent.getStringExtra(RestAlarm.EXTRA_NEXT))
    }
}
