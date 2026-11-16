package com.lifehub.app

import android.app.Application
import android.content.Context
import com.lifehub.app.di.AppContainer
import com.lifehub.app.notifications.Channels
import com.lifehub.app.notifications.NotificationScheduler
import kotlinx.coroutines.launch

class LifeHubApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Channels.ensure(this)
        container.settings.setTimezone(container.time.zone.id)
        container.appScope.launch {
            try {
                container.achievements.detectPeriods(
                    container.time.today(), container.settings.current().proteinTargetG, container.time.zone, container.time.now(),
                )
            } catch (_: Exception) {}
        }
        container.recoverCaptures()
        NotificationScheduler.schedule(this, container.settings.current().dailyCheckTime, replace = false)
    }
}

val Context.container: AppContainer get() = (applicationContext as LifeHubApp).container
