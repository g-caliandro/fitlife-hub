package com.lifehub.app.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lifehub.app.R
import com.lifehub.app.data.model.NotifType
import com.lifehub.app.navigation.DeepLinks

object Notifier {
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun channelFor(t: NotifType) = when (t) {
        NotifType.TRAIN -> Channels.TRAIN; NotifType.PROTEIN -> Channels.PROTEIN; NotifType.WEIGHT -> Channels.WEIGHT
        NotifType.ACHIEVEMENT_MEMORY -> Channels.ACHIEVEMENT
    }

    /** Returns true if the notification was handed to the system. */
    @Suppress("MissingPermission")
    fun post(context: Context, n: PendingNotification): Boolean {
        if (!hasPermission(context)) return false
        Channels.ensure(context)
        val notif = NotificationCompat.Builder(context, channelFor(n.type))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(n.title)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setAutoCancel(true)
            .setContentIntent(DeepLinks.pendingIntent(context, n.type.ordinal, n.route))
            .build()
        NotificationManagerCompat.from(context).notify(100 + n.type.ordinal, notif)
        return true
    }
}
