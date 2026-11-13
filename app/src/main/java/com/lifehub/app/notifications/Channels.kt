package com.lifehub.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object Channels {
    const val TRAIN = "reminders_train"
    const val PROTEIN = "reminders_protein"
    const val WEIGHT = "reminders_weight"
    const val ACHIEVEMENT = "reminders_achievement"
    const val STREAK = "reminders_streak"
    const val REST_TIMER = "rest_timer"
    const val REST_DONE = "rest_done"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        fun ch(id: String, name: String, importance: Int) = NotificationChannel(id, name, importance)
        nm.createNotificationChannels(
            listOf(
                ch(TRAIN, "Training reminders", NotificationManager.IMPORTANCE_DEFAULT),
                ch(PROTEIN, "Protein reminders", NotificationManager.IMPORTANCE_DEFAULT),
                ch(WEIGHT, "Weight reminders", NotificationManager.IMPORTANCE_DEFAULT),
                ch(ACHIEVEMENT, "Achievements", NotificationManager.IMPORTANCE_DEFAULT),
                ch(STREAK, "Streak reminders", NotificationManager.IMPORTANCE_DEFAULT),
                ch(REST_TIMER, "Rest timer", NotificationManager.IMPORTANCE_LOW),
                ch(REST_DONE, "Rest over", NotificationManager.IMPORTANCE_HIGH),
            )
        )
    }
}
