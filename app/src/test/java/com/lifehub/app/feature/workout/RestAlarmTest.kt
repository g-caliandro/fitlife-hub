package com.lifehub.app.feature.workout

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lifehub.app.notifications.RestAlarm
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RestAlarmTest {
    @Test fun scheduleThenCancel() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        RestAlarm.schedule(ctx, System.currentTimeMillis() + 60_000, "Pull-up")
        assertNotNull(shadowOf(am).peekNextScheduledAlarm())
        RestAlarm.cancel(ctx)
        org.junit.Assert.assertNull(shadowOf(am).peekNextScheduledAlarm())
    }
}
