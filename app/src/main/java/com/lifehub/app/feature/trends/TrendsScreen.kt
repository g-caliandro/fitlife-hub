package com.lifehub.app.feature.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.R
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.MetricKind
import com.lifehub.app.core.designsystem.components.DeltaGood
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhDeltaRow
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhMetricBar
import com.lifehub.app.core.designsystem.components.LhScreenHeader
import com.lifehub.app.core.designsystem.components.LhSectionHeader
import com.lifehub.app.core.designsystem.components.LhSegmentedControl
import com.lifehub.app.core.designsystem.components.LhStatTile
import com.lifehub.app.core.designsystem.components.LocalTabBottomPadding
import com.lifehub.app.core.designsystem.fmtClock
import com.lifehub.app.core.designsystem.fmtDelta
import com.lifehub.app.core.designsystem.fmtInt
import com.lifehub.app.core.designsystem.fmtKg
import com.lifehub.app.core.designsystem.fmtRange
import com.lifehub.app.core.ui.charts.BarChart
import com.lifehub.app.core.ui.charts.ChartPoint
import com.lifehub.app.core.ui.charts.LineChart
import com.lifehub.app.core.ui.charts.LineSeries
import com.lifehub.app.core.ui.charts.TooltipRow
import com.lifehub.app.domain.Metric
import com.lifehub.app.domain.fatPct
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

@Composable
fun TrendsRoute() {
    val vm: TrendsViewModel = viewModel(factory = TrendsViewModel.factory(LocalContext.current.container))
    val state by vm.state.collectAsStateWithLifecycle()
    TrendsScreen(state, TrendsCallbacks(vm::setPeriod, vm::previous, vm::next, vm::jumpToCurrent))
}

class TrendsCallbacks(
    val onPeriod: (TrendsPeriod) -> Unit = {},
    val onPrev: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onCurrent: () -> Unit = {},
)

private const val DASH = "—"

@Composable
fun TrendsScreen(state: TrendsUiState?, callbacks: TrendsCallbacks = TrendsCallbacks(), bodySelection: LocalDate? = null) {
    val c = LhTheme.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        LhScreenHeader(stringResource(R.string.trends_title), Icons.Filled.Insights, actions = {
            if (state != null) Box(Modifier.padding(end = 8.dp)) {
                LhSegmentedControl(TrendsPeriod.entries, state.period, callbacks.onPeriod) { it.label }
            }
        })
        if (state == null) return@Column
        LazyColumn(Modifier.fillMaxSize().testTag("trends-list"), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, LocalTabBottomPadding.current)) {
            item { PeriodBar(state, callbacks) }
            item {
                LhSectionHeader(stringResource(R.string.trends_training), icon = Icons.Outlined.FitnessCenter, topPadding = 16.dp)
                Spacer(Modifier.height(8.dp))
                TrainingTiles(state)
            }
            item {
                LhSectionHeader(stringResource(R.string.trends_nutrition), icon = Icons.Outlined.Restaurant)
                Spacer(Modifier.height(8.dp))
                NutritionCard(state)
                Spacer(Modifier.height(12.dp))
                DailyAverageCard(state)
            }
            item {
                LhSectionHeader(stringResource(R.string.trends_body), icon = Icons.Outlined.MonitorWeight)
                Spacer(Modifier.height(8.dp))
                BodyCard(state, bodySelection)
            }
        }
    }
}

/** ‹  range  › — tapping the range jumps back to the current period (2W/4W toggle sits in the header). */
@Composable
private fun PeriodBar(state: TrendsUiState, cb: TrendsCallbacks) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LhIconButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.trends_prev_period), cb.onPrev, variant = LhButtonVariant.Ghost)
        Text(
            fmtRange(state.range), style = LhType.tnum(LhType.label), color = LhTheme.colors.foreground, maxLines = 1,
            modifier = Modifier.weight(1f).clickable(onClick = cb.onCurrent).padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
        )
        LhIconButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.trends_next_period), cb.onNext, enabled = state.offset > 0, variant = LhButtonVariant.Ghost)
    }
}

private fun <T : Number> Metric<T>.intDeltaText(unit: String? = null): String? =
    delta?.let { fmtDelta(it, 0, unit) }

@Composable
private fun TrainingTiles(state: TrendsUiState) {
    val i = state.insights
    val gap = Arrangement.spacedBy(12.dp)
    Column(verticalArrangement = gap) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = gap) {
            LhStatTile(
                "Sessions", i.sessions.current?.toString() ?: DASH, Modifier.weight(1f).fillMaxHeight(),
                delta = i.sessions.delta, deltaText = i.sessions.intDeltaText(), deltaDecimals = 0,
            )
            val cur = i.adherence.first
            val pts = i.adherenceDeltaPts
            LhStatTile(
                "Plan workouts done", if (cur.planned == 0) DASH else "${cur.done} / ${cur.planned}", Modifier.weight(1f).fillMaxHeight(),
                delta = pts?.toDouble(), deltaText = pts?.let { fmtDelta(it.toDouble(), 0, "pts") }, deltaDecimals = 0,
                caption = if (cur.planned == 0) "No planned days" else "planned in period",
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = gap) {
            LhStatTile("Avg reps/set", i.avgRepsPerSet.current?.let { String.format(Locale.ENGLISH, "%.1f", it) } ?: DASH, Modifier.weight(1f).fillMaxHeight(),
                delta = i.avgRepsPerSet.delta, deltaText = fmtDelta(i.avgRepsPerSet.delta), good = DeltaGood.Up)
            val hold = i.holdSeconds
            LhStatTile(
                "Volume", fmtInt(i.volume.current), Modifier.weight(1f).fillMaxHeight(), unit = "reps",
                delta = i.volume.delta, deltaText = i.volume.intDeltaText(), good = DeltaGood.Up, deltaDecimals = 0,
                caption = if ((hold.current ?: 0) > 0 || (hold.previous ?: 0) > 0) "Holds ${fmtClock(hold.current ?: 0)}" else null,
            )
        }
    }
}

/** Calories and protein bar charts stacked, each with its period average and delta vs the previous period. */
@Composable
private fun NutritionCard(state: TrendsUiState) {
    val c = LhTheme.colors
    val i = state.insights
    val chart = Modifier.height(140.dp)
    LhCard(Modifier.fillMaxWidth().testTag("trends-nutrition"), contentPadding = PaddingValues(20.dp)) {
        ChartHeader(
            stringResource(R.string.trends_calories), i.avgKcal.current?.let { "Avg ${fmtInt(it)} kcal" }, i.avgKcal.delta,
            i.avgKcal.intDeltaText(), kcalDeltaGood(i.avgKcal.current, i.avgKcal.previous, state.kcalTarget),
        )
        Spacer(Modifier.height(8.dp))
        BarChart(
            state.range, state.macroPoints, chart, barColor = c.kcal, targetLine = state.kcalTarget?.toDouble(),
            targetLabel = state.kcalTarget?.let { fmtInt(it) }, valueFormatter = { fmtInt(it.toInt()) }, today = state.today, name = "Daily calories",
        )
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = c.border)
        ChartHeader(
            stringResource(R.string.trends_protein), i.avgProtein.current?.let { "Avg $it g" }, i.avgProtein.delta,
            i.avgProtein.intDeltaText("g"), DeltaGood.Up,
        )
        Spacer(Modifier.height(8.dp))
        BarChart(
            state.range, state.proteinPoints, chart, barColor = c.protein, targetLine = state.proteinTargetG.toDouble(),
            targetLabel = "${state.proteinTargetG} g", today = state.today, name = "Daily protein",
        )
    }
}

/** Title left; period average + coloured "vs prev" delta right (same style as the stat tiles). */
@Composable
private fun ChartHeader(title: String, avg: String?, delta: Double?, deltaText: String?, good: DeltaGood) {
    Row(Modifier.fillMaxWidth()) {
        Text(title, style = LhType.heading, color = LhTheme.colors.foreground, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(avg ?: DASH, style = LhType.tnum(LhType.label), color = LhTheme.colors.foreground, maxLines = 1)
            if (delta != null) LhDeltaRow(delta, deltaText, good, deltaDecimals = 0)
        }
    }
}

/**
 * Calories have no "higher is better": the better direction is towards the target. Good when the current average is
 * closer to the target than the previous one, bad when farther, neutral when equally close or without a target.
 * Expressed as the [DeltaGood] direction so [LhDeltaRow] colours the actual change.
 */
internal fun kcalDeltaGood(current: Int?, previous: Int?, target: Int?): DeltaGood {
    if (current == null || previous == null || target == null || current == previous) return DeltaGood.Neutral
    val now = abs(current - target); val before = abs(previous - target)
    val up = current > previous
    return when {
        now < before -> if (up) DeltaGood.Up else DeltaGood.Down
        now > before -> if (up) DeltaGood.Down else DeltaGood.Up
        else -> DeltaGood.Neutral
    }
}

/** ONT "Daily average": carbs / fat / protein over the logged days of the period vs targets, 8dp macro bars. */
@Composable
private fun DailyAverageCard(state: TrendsUiState) {
    val c = LhTheme.colors
    val a = state.averages
    LhCard(Modifier.fillMaxWidth().testTag("trends-daily-average"), contentPadding = PaddingValues(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Daily average", style = LhType.heading, color = c.foreground, modifier = Modifier.weight(1f))
            Text(a?.let { "${fmtInt(it.kcal)} kcal · ${it.days} d logged" } ?: "Nothing logged", style = LhType.tnum(LhType.captionMuted), maxLines = 1)
        }
        @Composable
        fun bar(metric: MetricKind, value: Int?, target: Int?) {
            Spacer(Modifier.height(12.dp))
            val t = target?.takeIf { it > 0 }
            LhMetricBar(
                metric, (value?.toString() ?: DASH) + (t?.let { " / $it g" } ?: " g"),
                if (value == null) 0f else if (t != null) value / t.toFloat() else 1f,
            )
        }
        bar(MetricKind.Carbs, a?.carbsG, state.carbsTargetG)
        bar(MetricKind.Fat, a?.fatG, state.fatTargetG)
        bar(MetricKind.Protein, a?.proteinG, state.proteinTargetG)
    }
}

/** Weight and muscle on one chart with a single shared kg axis; tap/drag for the day's weight, muscle and fat %. */
@Composable
private fun BodyCard(state: TrendsUiState, initialSelection: LocalDate?) {
    val c = LhTheme.colors
    val i = state.insights
    val weightColor = c.pull
    val muscleColor = c.accent
    val byDate = state.body.associateBy { it.date }
    LhCard(Modifier.fillMaxWidth().testTag("trends-body"), contentPadding = PaddingValues(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(weightColor, "Weight", i.weight.current)
            Legend(muscleColor, "Muscle", i.muscle.current)
        }
        Spacer(Modifier.height(8.dp))
        LineChart(
            state.range,
            listOf(
                LineSeries("Weight", state.body.map { ChartPoint(it.date, it.weightKg) }, weightColor, "kg"),
                LineSeries("Muscle", state.body.mapNotNull { e -> e.muscleKg?.let { ChartPoint(e.date, it) } }, muscleColor, "kg"),
            ),
            today = state.today,
            initialSelection = initialSelection,
            tooltip = { d ->
                val e = byDate.getValue(d)
                listOf(
                    TooltipRow("Weight ${fmtKg(e.weightKg)} kg", weightColor),
                    TooltipRow("Muscle ${e.muscleKg?.let { "${fmtKg(it)} kg" } ?: DASH}", muscleColor),
                    TooltipRow("Fat ${fatPct(e)?.let { "${fmtKg(it)} %" } ?: DASH}", c.fat),
                )
            },
        )
    }
}

@Composable
private fun Legend(color: Color, name: String, latest: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(name, style = LhType.caption, color = LhTheme.colors.foreground)
        Spacer(Modifier.width(4.dp))
        Text(latest?.let { "${fmtKg(it)} kg" } ?: DASH, style = LhType.tnum(LhType.captionMuted), maxLines = 1)
    }
}
