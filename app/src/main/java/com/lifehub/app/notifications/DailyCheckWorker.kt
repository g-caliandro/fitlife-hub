package com.lifehub.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lifehub.app.container
import com.lifehub.app.data.model.NotifType
import com.lifehub.app.data.model.SessionStatus
import com.lifehub.app.di.AppContainer
import com.lifehub.app.domain.asMacroDays
import com.lifehub.app.domain.mergeNutrition
import java.time.LocalDate

/** Snapshot of everything the notification rules look at. */
internal suspend fun ruleInput(c: AppContainer, today: LocalDate): RuleInput {
    val snap = c.backup.snapshot()
    val earliest = (snap.sessions.map { it.date } + snap.quickAdds.map { it.date } + snap.foodEntries.map { it.date }).minOrNull()
    return RuleInput(
        today = today, settings = c.settings.current(),
        lastNotified = NotifType.entries.associateWith { c.settings.lastNotified(it) },
        plans = snap.plans,
        completed = snap.sessions.filter { it.status == SessionStatus.COMPLETED },
        hasInProgressToday = snap.sessions.any { it.status == SessionStatus.IN_PROGRESS && it.date == today },
        macros = mergeNutrition(snap.quickAdds, snap.foodEntries).asMacroDays().filter { it.date >= today.minusDays(14) && it.date < today },
        latestBodyDate = snap.bodyEntries.maxOfOrNull { it.date },
        earliestDataDate = earliest,
        achievements = snap.achievements,
        unlocked = snap.achievements.filter {
            it.kind.isPeriod && it.dedupeKey !in c.settings.notifiedAchievementKeys() && it.achievedOn >= today.minusDays(14)
        },
        achievementSentDates = c.settings.achievementSentDates(),
        zone = c.time.zone,
    )
}

class DailyCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            val c = applicationContext.container
            val today = c.time.today()
            c.achievements.detectPeriods(today, c.settings.current().proteinTargetG, c.time.zone, c.time.now())
            NotificationRules.evaluate(ruleInput(c, today)).forEach { n ->
                if (Notifier.post(applicationContext, n)) {
                    c.settings.setLastNotified(n.type, today)
                    if (n.type == NotifType.ACHIEVEMENT_MEMORY) {
                        c.settings.addAchievementSentDate(today)
                        n.key?.let(c.settings::addNotifiedAchievementKey)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return Result.success()
    }
}

/** Evening "keep your streak" check, see [NotificationRules.streak]. */
class StreakCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            val c = applicationContext.container
            val today = c.time.today()
            NotificationRules.evaluateStreak(ruleInput(c, today))?.let { n ->
                if (Notifier.post(applicationContext, n)) c.settings.setLastNotified(n.type, today)
            }
        } catch (_: Exception) {
        }
        return Result.success()
    }
}
