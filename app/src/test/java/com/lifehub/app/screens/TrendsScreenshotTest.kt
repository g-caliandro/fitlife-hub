package com.lifehub.app.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.ui.charts.ChartPoint
import com.lifehub.app.data.model.BodyEntry
import com.lifehub.app.data.model.HomePeriod
import com.lifehub.app.domain.InsightInput
import com.lifehub.app.domain.Periods
import com.lifehub.app.domain.computeInsights
import com.lifehub.app.domain.macroAverages
import com.lifehub.app.feature.trends.TrendsScreen
import com.lifehub.app.feature.trends.TrendsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 11, 12)

/** Sparse body log over ~4 weeks (weight ~77, muscle ~35); one entry without muscle (Tue 10 Nov) to show the gap bridge. */
private fun trendsBody(empty: Boolean): List<BodyEntry> = if (empty) emptyList() else
    listOf(-27, -24, -20, -17, -13, -10, -8, -6, -3, -2, 0).mapIndexed { k, off ->
        val w = 78.1 - k * 0.12 + listOf(0.0, 0.3, -0.2, 0.25, -0.1, 0.35, -0.15, 0.1, -0.3, 0.2, 0.0)[k]
        BodyEntry(TODAY.plusDays(off.toLong()), w, if (off == -2) null else 34.6 + k * 0.07 + (k % 3) * 0.12, w * (0.165 - k * 0.001), Instant.EPOCH)
    }

private fun trendsState(period: HomePeriod = HomePeriod.ONE_WEEK, empty: Boolean = false): TrendsUiState {
    val today = TODAY
    val cur = Periods.period(today, period, 0)
    val prev = Periods.previous(cur)
    val macros = Fixture.macros(empty)
    val body = trendsBody(empty)
    val ins = computeInsights(InsightInput(today, cur, prev, listOf(Fixture.plan, Fixture.plan2), Fixture.sessions(empty), macros, body, 150))
    val cm = macros.filter { it.date in cur }
    val cb = body.filter { it.date in cur }
    return TrendsUiState(
        today, period, 0, cur, ins, 150,
        cm.map { ChartPoint(it.date, it.kcal.toDouble()) }, cm.map { ChartPoint(it.date, it.proteinG.toDouble()) },
        cb,
        kcalTarget = if (empty) null else 2600, carbsTargetG = 250, fatTargetG = 80,
        averages = macroAverages(macros, cur),
    )
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1500dp-xxhdpi")
class TrendsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean, state: TrendsUiState, bodySelection: LocalDate? = null) {
        rule.setContent { LhTheme(darkTheme = dark) { TrendsScreen(state, bodySelection = bodySelection) } }
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun trendsLight() = shot("trends-light", false, trendsState())
    @Test fun trendsDark() = shot("trends-dark", true, trendsState())
    @Test fun trends2w() = shot("trends-2w-light", false, trendsState(HomePeriod.TWO_WEEKS))
    @Test fun trends2wDark() = shot("trends-2w-dark", true, trendsState(HomePeriod.TWO_WEEKS))
    @Test fun trendsTooltip() = shot("trends-tooltip-light", false, trendsState(HomePeriod.TWO_WEEKS), TODAY.minusDays(6))
    @Test fun trendsTooltipDark() = shot("trends-tooltip-dark", true, trendsState(HomePeriod.TWO_WEEKS), TODAY.minusDays(2))
    @Test fun trendsEmpty() = shot("trends-empty-light", false, trendsState(empty = true))
}
