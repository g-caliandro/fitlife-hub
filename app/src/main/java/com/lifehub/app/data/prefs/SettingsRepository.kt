package com.lifehub.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.lifehub.app.data.model.HomePeriod
import com.lifehub.app.data.model.NotifType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class Settings(
    val proteinTargetG: Int = 150,
    /** Optional daily targets (null = not set). */
    val kcalTarget: Int? = null,
    val carbsTargetG: Int? = null,
    val fatTargetG: Int? = null,
    val notifyTrain: Boolean = true,
    val notifyProtein: Boolean = true,
    val notifyWeight: Boolean = true,
    val notifyAchievements: Boolean = true,
    val dailyCheckTime: LocalTime = LocalTime.of(18, 0),   // pref key "dailyCheckMinute": Int minute-of-day
    val homePeriod: HomePeriod = HomePeriod.ONE_WEEK,
    val timezone: String = ZoneId.systemDefault().id,        // informational
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("lifehub_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings

    fun current(): Settings = settings.value

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            proteinTargetG = prefs.getInt("proteinTargetG", d.proteinTargetG),
            kcalTarget = prefs.getInt("kcalTarget", -1).takeIf { it >= 0 },
            carbsTargetG = prefs.getInt("carbsTargetG", -1).takeIf { it >= 0 },
            fatTargetG = prefs.getInt("fatTargetG", -1).takeIf { it >= 0 },
            notifyTrain = prefs.getBoolean("notifyTrain", d.notifyTrain),
            notifyProtein = prefs.getBoolean("notifyProtein", d.notifyProtein),
            notifyWeight = prefs.getBoolean("notifyWeight", d.notifyWeight),
            notifyAchievements = prefs.getBoolean("notifyAchievements", d.notifyAchievements),
            dailyCheckTime = LocalTime.ofSecondOfDay(prefs.getInt("dailyCheckMinute", d.dailyCheckTime.hour * 60 + d.dailyCheckTime.minute) * 60L),
            homePeriod = prefs.getString("homePeriod", null)
                ?.let { n -> HomePeriod.entries.firstOrNull { it.name == n } } ?: d.homePeriod,
            timezone = prefs.getString("timezone", null) ?: d.timezone,
        )
    }

    private fun write(s: Settings) {
        prefs.edit()
            .putInt("proteinTargetG", s.proteinTargetG)
            .putInt("kcalTarget", s.kcalTarget ?: -1)
            .putInt("carbsTargetG", s.carbsTargetG ?: -1)
            .putInt("fatTargetG", s.fatTargetG ?: -1)
            .putBoolean("notifyTrain", s.notifyTrain)
            .putBoolean("notifyProtein", s.notifyProtein)
            .putBoolean("notifyWeight", s.notifyWeight)
            .putBoolean("notifyAchievements", s.notifyAchievements)
            .putInt("dailyCheckMinute", s.dailyCheckTime.hour * 60 + s.dailyCheckTime.minute)
            .putString("homePeriod", s.homePeriod.name)
            .putString("timezone", s.timezone)
            .apply()
        _settings.value = load()
    }

    fun setProteinTarget(g: Int) = write(current().copy(proteinTargetG = g.coerceIn(0, 500)))

    fun setKcalTarget(v: Int?) = write(current().copy(kcalTarget = v?.coerceIn(0, 10_000)))
    fun setCarbsTarget(v: Int?) = write(current().copy(carbsTargetG = v?.coerceIn(0, 1_500)))
    fun setFatTarget(v: Int?) = write(current().copy(fatTargetG = v?.coerceIn(0, 800)))

    fun setNotify(type: NotifType, enabled: Boolean) = write(
        when (type) {
            NotifType.TRAIN -> current().copy(notifyTrain = enabled)
            NotifType.PROTEIN -> current().copy(notifyProtein = enabled)
            NotifType.WEIGHT -> current().copy(notifyWeight = enabled)
            NotifType.ACHIEVEMENT_MEMORY -> current().copy(notifyAchievements = enabled)
        }
    )

    fun setDailyCheckTime(time: LocalTime) = write(current().copy(dailyCheckTime = time.withSecond(0).withNano(0)))
    fun setHomePeriod(p: HomePeriod) = write(current().copy(homePeriod = p))
    fun setTimezone(id: String) = write(current().copy(timezone = id))
    fun replace(s: Settings) = write(s)

    fun lastNotified(type: NotifType): LocalDate? =
        prefs.getLong("lastNotified_${type.name}", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)

    fun setLastNotified(type: NotifType, date: LocalDate) {
        prefs.edit().putLong("lastNotified_${type.name}", date.toEpochDay()).apply()
    }

    // ---- achievements bookkeeping (not exported) ----
    fun dismissedAchievementKeys(): Set<String> = prefs.getStringSet("dismissedAchievementKeys", emptySet()).orEmpty().toSet()
    fun dismissAchievementKey(key: String) { prefs.edit().putStringSet("dismissedAchievementKeys", dismissedAchievementKeys() + key).apply() }
    fun clearDismissedAchievementKeys() { prefs.edit().remove("dismissedAchievementKeys").apply() }

    /** Achievement keys already announced by an "Achievement unlocked" notification. */
    fun notifiedAchievementKeys(): Set<String> = prefs.getStringSet("notifiedAchievementKeys", emptySet()).orEmpty().toSet()
    fun addNotifiedAchievementKey(key: String) { prefs.edit().putStringSet("notifiedAchievementKeys", notifiedAchievementKeys() + key).apply() }

    /** Days on which an ACHIEVEMENT_MEMORY notification was sent (last 14 kept). */
    fun achievementSentDates(): List<LocalDate> =
        prefs.getString("achievementSentDates", "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }.map(LocalDate::ofEpochDay)
    fun addAchievementSentDate(date: LocalDate) {
        val kept = (achievementSentDates() + date).distinct().sortedDescending().take(14)
        prefs.edit().putString("achievementSentDates", kept.joinToString(",") { it.toEpochDay().toString() }).apply()
    }

    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean("notificationPermissionAsked", false)
        set(v) { prefs.edit().putBoolean("notificationPermissionAsked", v).apply() }

    fun clear() {
        prefs.edit().clear().apply()
        _settings.value = load()
    }
}
