package com.lifehub.app.navigation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lifehub.app.MainActivity

object DeepLinks {
    const val EXTRA_ROUTE = "com.lifehub.app.ROUTE"

    fun intent(context: Context, route: String): Intent =
        Intent(context, MainActivity::class.java).putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun pendingIntent(context: Context, requestCode: Int, route: String): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent(context, route), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
