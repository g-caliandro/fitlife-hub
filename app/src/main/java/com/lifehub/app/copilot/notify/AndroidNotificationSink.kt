package com.lifehub.app.copilot.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.lifehub.app.R
import com.lifehub.app.container
import com.lifehub.app.navigation.DeepLinks
import com.lifehub.app.notifications.Channels
import com.lifehub.app.notifications.Notifier
import kotlinx.coroutines.launch

/**
 * Posts [NotificationSpec]s with NotificationCompat. Actions are broadcasts to [CopilotActionReceiver] (work with the app
 * closed); every action has its own request code so a reply never reaches the wrong request. Reply uses RemoteInput and a
 * MUTABLE PendingIntent (required for the system to add the typed text); all the others are IMMUTABLE.
 */
class AndroidNotificationSink(context: Context) : NotificationSink {
    private val ctx = context.applicationContext

    @Suppress("MissingPermission")
    override fun post(spec: NotificationSpec): Boolean {
        if (!Notifier.hasPermission(ctx)) return false
        Channels.ensure(ctx)
        val b = NotificationCompat.Builder(ctx, spec.channel.id)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.text))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setCategory(if (spec.channel == CopilotChannel.ERROR) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(DeepLinks.pendingIntent(ctx, ("open" + spec.tag).hashCode(), spec.route))
        spec.actions.forEach { b.addAction(action(spec, it)) }
        return try {
            NotificationManagerCompat.from(ctx).notify(spec.tag, NOTIFICATION_ID, b.build()); true
        } catch (_: SecurityException) { false }
    }

    override fun cancel(tag: String) = NotificationManagerCompat.from(ctx).cancel(tag, NOTIFICATION_ID)

    private fun action(spec: NotificationSpec, a: NotificationAction): NotificationCompat.Action {
        val intent = Intent(ctx, CopilotActionReceiver::class.java)
            .setAction(ACTION_PREFIX + a.kind.name)
            .putExtra(EXTRA_CAPTURE, spec.captureId)
            .putExtra(EXTRA_PENDING, spec.pendingId)
            .putExtra(EXTRA_OPTION, a.option ?: -1)
        val code = (spec.tag + a.kind.name + (a.option ?: "")).hashCode()
        val mutable = a.kind == ActionKind.REPLY
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else if (mutable) 0 else PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(ctx, code, intent, flags)
        val builder = NotificationCompat.Action.Builder(0, a.label, pi)
        if (a.kind == ActionKind.REPLY) builder.addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Your answer").build()).setAllowGeneratedReplies(false)
        return builder.build()
    }

    companion object {
        const val NOTIFICATION_ID = 7001
        const val ACTION_PREFIX = "com.lifehub.app.copilot."
        const val EXTRA_CAPTURE = "captureId"
        const val EXTRA_PENDING = "pendingId"
        const val EXTRA_OPTION = "option"
        const val KEY_REPLY = "reply"
    }
}

/** Handles Undo / Retry / Choose / Reply from copilot notifications, also with the app closed and the phone locked. */
class CopilotActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.action?.removePrefix(AndroidNotificationSink.ACTION_PREFIX)?.let { runCatching { ActionKind.valueOf(it) }.getOrNull() } ?: return
        val captureId = intent.getStringExtra(AndroidNotificationSink.EXTRA_CAPTURE)
        val pendingId = intent.getStringExtra(AndroidNotificationSink.EXTRA_PENDING)
        val option = intent.getIntExtra(AndroidNotificationSink.EXTRA_OPTION, -1).takeIf { it >= 0 }
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(AndroidNotificationSink.KEY_REPLY)?.toString()
        val c = context.container
        val result = goAsync()
        c.appScope.launch {
            try {
                when (kind) {
                    ActionKind.UNDO -> captureId?.let { c.copilotActions.undo(it) }
                    ActionKind.RETRY -> captureId?.let { c.copilotActions.retry(it) }
                    ActionKind.CHOOSE -> pendingId?.let { c.copilotActions.answer(it, option = option) }
                    ActionKind.REPLY -> pendingId?.let { id ->
                        // An empty reply leaves the request open; the notification is re-posted to close the input UI.
                        if (!c.copilotActions.answer(id, text = reply)) c.notificationCenter.repostIfOpen(id)
                    }
                }
            } catch (_: Exception) {
                // Never crash a broadcast; the request / capture stays as it was and is still in the inbox.
            } finally { result.finish() }
        }
    }
}
