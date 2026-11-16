package com.lifehub.app.copilot.tools

import com.lifehub.app.copilot.agent.DailySummaryCard
import com.lifehub.app.data.prefs.Settings
import com.lifehub.app.domain.DayNutrition
import java.time.LocalDate

/**
 * The day's totals exactly as the diary shows them (rounded `DayNutrition.asMacroDay()` values, food entries + quick
 * adds), the targets from Settings (a target <= 0 counts as not set, like the diary rings) and the remaining amounts.
 * Every number is computed here, by the app; the model only reports them.
 */
fun dailySummary(date: LocalDate, day: DayNutrition?, settings: Settings): DailySummaryCard {
    val m = day?.asMacroDay()
    fun target(v: Int?) = v?.takeIf { it > 0 }
    val kcal = m?.kcal ?: 0
    val p = m?.proteinG ?: 0; val c = m?.carbsG ?: 0; val f = m?.fatG ?: 0
    val pKnown = m?.proteinKnown ?: true; val cKnown = m?.carbsKnown ?: true; val fKnown = m?.fatKnown ?: true
    val kT = target(settings.kcalTarget); val pT = target(settings.proteinTargetG)
    val cT = target(settings.carbsTargetG); val fT = target(settings.fatTargetG)
    fun left(t: Int?, v: Int, known: Boolean) = if (t != null && known) t - v else null
    return DailySummaryCard(
        date = date.toString(), kcal = kcal, proteinG = p, carbsG = c, fatG = f,
        kcalTarget = kT, proteinTargetG = pT, carbsTargetG = cT, fatTargetG = fT,
        kcalRemaining = left(kT, kcal, true), proteinRemainingG = left(pT, p, pKnown),
        carbsRemainingG = left(cT, c, cKnown), fatRemainingG = left(fT, f, fKnown),
        proteinKnown = pKnown, carbsKnown = cKnown, fatKnown = fKnown,
        proteinComplete = m?.proteinComplete ?: true, carbsComplete = m?.carbsComplete ?: true, fatComplete = m?.fatComplete ?: true,
        items = (day?.entries?.size ?: 0) + (day?.quickAdds?.size ?: 0),
    )
}
