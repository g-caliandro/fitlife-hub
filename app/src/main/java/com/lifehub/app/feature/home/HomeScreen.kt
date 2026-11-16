package com.lifehub.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.R
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.DayTypeIcons
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.MetricKind
import com.lifehub.app.core.designsystem.color
import com.lifehub.app.core.designsystem.components.LhAddRow
import com.lifehub.app.core.designsystem.components.LhBadge
import com.lifehub.app.core.designsystem.components.LhBadgeVariant
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhIconAction
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhIconSquare
import com.lifehub.app.core.designsystem.components.LhListItem
import com.lifehub.app.core.designsystem.components.LhMacroCard
import com.lifehub.app.core.designsystem.components.LhMetricBar
import com.lifehub.app.core.designsystem.components.LhScreenHeader
import com.lifehub.app.core.designsystem.components.LhSectionHeader
import com.lifehub.app.core.designsystem.components.LhSeeAllChip
import com.lifehub.app.core.designsystem.components.LhValueChip
import com.lifehub.app.core.designsystem.fmtInt
import com.lifehub.app.core.designsystem.fmtKg
import com.lifehub.app.feature.food.FoodThumb
import com.lifehub.app.feature.food.fmtGrams
import com.lifehub.app.navigation.Routes
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeRoute(onNavigate: (String) -> Unit) {
    val c = LocalContext.current.container
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(c))
    val state by vm.state.collectAsStateWithLifecycle()
    HomeScreen(state, onNavigate, onStreakSeen = vm::streakCelebrated)
}

@Composable
fun HomeScreen(state: HomeUiState?, onNavigate: (String) -> Unit = {}, onStreakSeen: () -> Unit = {}, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val nav = onNavigate
    // Streak sheet opened from the badge: local UI state only, never marks the daily auto-celebration as seen.
    var streakOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(c.background)) {
        // Profile (You) = profile + all settings; replaces the old gear.
        LhScreenHeader(stringResource(R.string.home_title), Icons.Filled.Home, actions = {
            // Duolingo-style training streak (replaces the consistency achievements); tap -> streak sheet + week strip.
            if (state != null) StreakBadge(state.streakDays, { streakOpen = true })
            LhIconAction(Icons.Outlined.AccountCircle, stringResource(R.string.home_profile), { nav(Routes.YOU) }, Modifier.testTag("home-profile"))
        })
        if (state == null) return@Column
        if (state.celebrateStreak) StreakCelebration(state.streakDays, state.streakWeek, onDismiss = onStreakSeen)
        else if (streakOpen) StreakCelebration(state.streakDays, state.streakWeek, extended = false) { streakOpen = false }
        LazyColumn(Modifier.fillMaxSize().testTag("home-list"), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, com.lifehub.app.core.designsystem.components.LocalTabBottomPadding.current)) {
            item {
                LhSectionHeader(
                    stringResource(R.string.home_today), topPadding = 0.dp,
                    action = { Text(state.today.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)), style = LhType.captionMuted) },
                )
                Spacer(Modifier.height(8.dp))
                val b = state.latestBody
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LhValueChip(
                        if (b == null) "Log weight" else "${fmtKg(b.weightKg)} kg", icon = Icons.Outlined.MonitorWeight,
                        trailingIcon = if (b == null) Icons.Outlined.Add else Icons.Outlined.Edit,
                        onClick = { nav(Routes.bodyEdit(state.today)) }, modifier = Modifier.testTag("home-weight-chip"),
                    )
                }
                Spacer(Modifier.height(12.dp))
                CaloriesCard(state)
                Spacer(Modifier.height(12.dp))
                MacroCards(state)
            }
            item {
                LhSectionHeader(stringResource(R.string.home_training), icon = Icons.Outlined.FitnessCenter)
                TrainingToday(state, nav)
            }
            item {
                LhSectionHeader(stringResource(R.string.home_goals), icon = Icons.Outlined.Flag, action = { LhSeeAllChip({ nav(Routes.GOALS) }, label = stringResource(R.string.home_all)) })
                if (state.goals.isEmpty()) {
                    LhCard(Modifier.fillMaxWidth()) {
                        LhEmptyState(stringResource(R.string.home_no_goals), icon = Icons.Outlined.Flag, actionText = stringResource(R.string.home_add_goal),
                            onAction = { nav(Routes.goalEdit()) }, actionIcon = Icons.Outlined.Add)
                    }
                }
            }
            items(state.goals.size) { i ->
                val g = state.goals[i]
                if (i > 0) Spacer(Modifier.height(12.dp))
                com.lifehub.app.core.ui.GoalProgressCard(g, onClick = { nav(Routes.goal(g.id)) })
            }
            item {
                LhSectionHeader("Food today", icon = Icons.Outlined.Restaurant, action = { LhSeeAllChip({ nav(Routes.CALENDAR) }, label = "See diary") })
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.todayFood.take(3).forEach { e ->
                        LhListItem(
                            e.name, subtitle = "${fmtGrams(e.grams)} g", trailingText = "${fmtInt(e.kcal.roundToInt())} kcal",
                            leading = { FoodThumb(e.name, e.imageUrl) },
                            onClick = { nav(Routes.foodDetail(e.foodId ?: "none", e.date, e.id)) },
                        )
                    }
                    LhAddRow({ nav(Routes.foodAdd(state.today)) }, label = if (state.todayFood.isEmpty()) "Add food" else null, modifier = Modifier.testTag("home-add-food"))
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(count: Int, content: @Composable (Int) -> Unit) =
    items(count) { content(it) }

/** Today's calories as a compact bar (replaces the old ring), first of the macro bars. */
@Composable
private fun CaloriesCard(state: HomeUiState) {
    val c = LhTheme.colors
    val eaten = state.todayMacro?.kcal ?: 0
    val kt = state.kcalTarget?.takeIf { it > 0 }
    LhCard(Modifier.fillMaxWidth().testTag("home-kcal-card"), contentPadding = PaddingValues(16.dp)) {
        LhMetricBar(
            MetricKind.Calories,
            if (kt != null) "${fmtInt(eaten)} / ${fmtInt(kt)} kcal" else "${fmtInt(eaten)} kcal",
            if (kt != null) eaten / kt.toFloat() else 0f,
            color = if (kt != null && eaten > kt) c.warning else MetricKind.Calories.color(),
            caption = when {
                kt == null -> "No calorie target set"
                eaten > kt -> "${fmtInt(eaten - kt)} kcal over"
                else -> "${fmtInt(kt - eaten)} kcal left"
            },
        )
    }
}

@Composable
private fun MacroCards(state: HomeUiState) {
    val c = LhTheme.colors
    val m = state.todayMacro
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LhMacroCard("carbs", c.carbs, (m?.carbsG ?: 0).toDouble(), state.carbsTargetG?.toDouble(), Modifier.weight(1f), metric = MetricKind.Carbs, unknown = m?.carbsKnown == false)
        LhMacroCard("fat", c.fat, (m?.fatG ?: 0).toDouble(), state.fatTargetG?.toDouble(), Modifier.weight(1f), metric = MetricKind.Fat, unknown = m?.fatKnown == false)
        LhMacroCard("protein", c.protein, (m?.proteinG ?: 0).toDouble(), state.proteinTargetG.toDouble(), Modifier.weight(1f), metric = MetricKind.Protein, unknown = m?.proteinKnown == false)
    }
}

/**
 * Today's training. Pending session(s) get a "Today" badge and a primary-tinted border; a muted footer row reminds
 * of tomorrow's scheduled plan(s). States: pending / done today / rest day / no plans / workout in progress.
 */
@Composable
private fun TrainingToday(state: HomeUiState, nav: (String) -> Unit) {
    val c = LhTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.workoutInProgress) {
            LhListItem(
                "Workout in progress", subtitle = "Pick up where you left off", icon = Icons.Outlined.FitnessCenter,
                trailing = { LhIconButton(Icons.Outlined.PlayArrow, stringResource(R.string.home_resume_workout), { nav(Routes.WORKOUT_LIVE) }, variant = LhButtonVariant.Primary) },
                onClick = { nav(Routes.WORKOUT_LIVE) },
            )
        }
        val showMain = !state.workoutInProgress || state.todayPlans.isNotEmpty()
        if (!showMain && state.tomorrowPlans.isEmpty()) return@Column
        val due = !state.workoutInProgress && state.todayPlans.any { !it.done }
        val shape = LhRadius.cardShape
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.card, shape)
                .border(1.dp, if (due) c.primary.copy(alpha = 0.55f) else c.border, shape)
                .testTag("home-training"),
        ) {
            if (showMain) when {
                !state.hasPlans -> LhListItem(
                    "No plans yet", icon = Icons.Outlined.FitnessCenter, iconTint = c.mutedForeground,
                    standalone = false, onClick = { nav(Routes.planEdit()) },
                    trailing = { LhButton("Add plan", { nav(Routes.planEdit()) }, size = LhButtonSize.Sm, variant = LhButtonVariant.Secondary) },
                )
                state.todayPlans.isEmpty() -> LhListItem("Rest day", subtitle = "Recover and eat well", icon = Icons.Outlined.Bedtime, iconTint = c.mutedForeground, standalone = false)
                else -> state.todayPlans.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(color = c.border, thickness = 1.dp)
                    TodayPlanRow(p, nav)
                }
            }
            if (state.tomorrowPlans.isNotEmpty()) {
                if (showMain) HorizontalDivider(color = c.border, thickness = 1.dp)
                TomorrowRow(state.tomorrowPlans, nav)
            }
        }
    }
}

@Composable
private fun TodayPlanRow(p: TodayPlan, nav: (String) -> Unit) {
    val c = LhTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { nav(Routes.workoutStart(p.id)) }.padding(12.dp)
            .then(if (p.done) Modifier else Modifier.testTag("home-start-${p.id}")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (p.dayType != null) LhIconSquare(p.dayType) else LhIconSquare(Icons.Outlined.FitnessCenter)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = LhType.bodyLarge, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.done) {
                    Text("Done today", style = LhType.body, color = c.mutedForeground, maxLines = 1)
                } else {
                    LhBadge(stringResource(R.string.home_today), variant = LhBadgeVariant.Accent)
                    if (p.exerciseCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("${p.exerciseCount} exercises", style = LhType.body, color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        if (p.done) Icon(Icons.Outlined.CheckCircle, "Done", tint = c.success, modifier = Modifier.size(24.dp))
        else LhButton("Start", { nav(Routes.workoutStart(p.id)) }, size = LhButtonSize.Sm)
    }
}

/** Compact muted reminder: "[icon] Tomorrow · Legs & Core" -> plan detail. */
@Composable
private fun TomorrowRow(plans: List<TodayPlan>, nav: (String) -> Unit) {
    val c = LhTheme.colors
    val first = plans.first()
    Row(
        Modifier.fillMaxWidth().clickable { nav(Routes.plan(first.id)) }.padding(horizontal = 16.dp, vertical = 12.dp).testTag("home-tomorrow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(first.dayType?.let { DayTypeIcons.of(it) } ?: Icons.Outlined.FitnessCenter, null, Modifier.size(16.dp), tint = c.mutedForeground)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.home_tomorrow) + " · " + plans.joinToString(", ") { it.name },
            style = LhType.captionMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
