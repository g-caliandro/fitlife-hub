package com.lifehub.app.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.outlined.Bolt
import com.lifehub.app.core.designsystem.components.LhScreenHeader
import com.lifehub.app.core.designsystem.components.LhListItem
import com.lifehub.app.core.designsystem.components.LhIconSquare
import com.lifehub.app.core.designsystem.components.LhAddRow
import com.lifehub.app.core.designsystem.components.LhKcalChip
import com.lifehub.app.core.designsystem.components.LhMacroRingStat
import com.lifehub.app.core.designsystem.components.LhCollapsibleCard
import com.lifehub.app.core.designsystem.dayTypeContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.lifehub.app.core.designsystem.components.LhIconAction
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.R
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.DayTypeIcon
import com.lifehub.app.core.designsystem.MetricIcon
import com.lifehub.app.core.designsystem.MetricKind
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.components.LhProgressBar
import com.lifehub.app.core.designsystem.components.LhSectionHeader
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.dayType
import com.lifehub.app.core.designsystem.fmtDate
import com.lifehub.app.core.designsystem.fmtDuration
import com.lifehub.app.core.designsystem.fmtInt
import com.lifehub.app.core.designsystem.fmtKg
import com.lifehub.app.core.designsystem.fmtWeight
import com.lifehub.app.core.designsystem.label
import com.lifehub.app.data.model.DayType
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.domain.Periods
import com.lifehub.app.feature.food.FoodEntryRow
import com.lifehub.app.feature.food.SwipeToDeleteEntry
import com.lifehub.app.domain.fatPct
import com.lifehub.app.navigation.Routes
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private const val PAGES = 4001
private const val START = 2000
private val CELL_HEIGHT = 52.dp

@Composable
fun CalendarRoute(onNavigate: (String) -> Unit) {
    val c = LocalContext.current.container
    val vm: CalendarViewModel = viewModel(factory = CalendarViewModel.factory(c))
    val state by vm.state.collectAsState()
    val pager = rememberPagerState(initialPage = START) { PAGES }
    // "Open in diary" from a copilot card: select that day and show its month.
    val jump by com.lifehub.app.navigation.DiaryJump.date.collectAsState()
    androidx.compose.runtime.LaunchedEffect(jump) {
        val d = jump ?: return@LaunchedEffect
        com.lifehub.app.navigation.DiaryJump.consume()
        vm.select(d)
        val months = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(state.today), YearMonth.from(d)).toInt()
        pager.scrollToPage((START + months).coerceIn(0, PAGES - 1))
    }
    CalendarScreen(state, vm::select, onNavigate, pagerState = pager, onDeleteFood = vm::deleteFood, onCopyYesterday = vm::copyYesterday, onClearDay = vm::clearDay, onDeleteQuickAdd = vm::deleteQuickAdd)
}

@Composable
fun CalendarScreen(
    state: CalendarState,
    onDayClick: (LocalDate) -> Unit,
    onNavigate: (String) -> Unit,
    pagerState: PagerState = rememberPagerState(initialPage = START) { PAGES },
    initiallyExpanded: Set<String> = emptySet(),
    onDeleteFood: (com.lifehub.app.data.model.FoodEntry) -> Unit = {},
    onCopyYesterday: (LocalDate) -> Unit = {},
    onClearDay: (LocalDate) -> Unit = {},
    onDeleteQuickAdd: (com.lifehub.app.data.model.QuickAddEntry) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val c = LhTheme.colors
    val base = remember(state.today) { YearMonth.from(state.today) }
    val month = base.plusMonths((pagerState.currentPage - START).toLong())
    val dow = remember { DayOfWeek.MONDAY }
    val diary = com.lifehub.app.navigation.LocalDiaryDate.current
    androidx.compose.runtime.LaunchedEffect(state.selected) { diary.value = state.selected }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { diary.value = null } }
    Column(Modifier.fillMaxSize().background(c.background)) {
        LhScreenHeader(stringResource(R.string.cal_title), Icons.Filled.Book, actions = {
            LhIconAction(Icons.Outlined.Today, stringResource(R.string.cal_today), {
                onDayClick(state.today)
                scope.launch { pagerState.animateScrollToPage(START) }
            })
        })
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("calendar-scroll")) {
            LhCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LhIconButton(Icons.Outlined.ChevronLeft, stringResource(R.string.cal_prev), {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    })
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year,
                        style = LhType.heading, color = c.foreground, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    LhIconButton(Icons.Outlined.ChevronRight, stringResource(R.string.cal_next), {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    })
                }
                val summary = remember(month, state.today, state.flags, state.missedTraining) {
                    DiaryFlags.monthSummary(month, state.today, state.flags, state.missedTraining)
                }
                val dueInMonth = remember(month, state.goalsDue) { DiaryFlags.goalsIn(month, state.goalsDue) }
                val reachedInMonth = remember(month, state.goalsReached) { DiaryFlags.goalsIn(month, state.goalsReached) }
                MonthSummaryRow(summary, dueInMonth, reachedInMonth, Modifier.padding(top = 4.dp, bottom = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
                    for (i in 0 until 7) {
                        Text(
                            dow.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                            style = LhType.captionMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                        )
                    }
                }
                HorizontalPager(pagerState, Modifier.fillMaxWidth().height(CELL_HEIGHT * 6)) { page ->
                    MonthGrid(base.plusMonths((page - START).toLong()), state, onDayClick)
                }
            }
            val day = state.day
            if (day != null && day.date == state.selected) {
                DayDetail(
                    day, state.today, state.proteinTarget, onNavigate, initiallyExpanded, Modifier, onDeleteFood,
                    state.kcalTarget, state.carbsTarget, state.fatTarget, onCopyYesterday, onClearDay,
                    goalsDue = state.goalsDue[day.date].orEmpty(), goalsReached = state.goalsReached[day.date].orEmpty(),
                    onDeleteQuickAdd = onDeleteQuickAdd,
                )
            }
            Spacer(Modifier.height(com.lifehub.app.core.designsystem.components.LocalTabBottomPadding.current))
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, state: CalendarState, onDayClick: (LocalDate) -> Unit) {
    val days = remember(month) { Periods.monthGrid(month) }
    Column(Modifier.fillMaxWidth()) {
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    val inMonth = YearMonth.from(d) == month
                    DayCell(
                        d, inMonth = inMonth, isToday = d == state.today, selected = d == state.selected,
                        // Faded neighbour-month days: plain number only (no markers, no missed styling).
                        flags = if (inMonth) state.flags[d] else null,
                        trained = inMonth && d in state.trainedDays,
                        missed = inMonth && d in state.missedTraining,
                        star = when {
                            !inMonth -> null
                            !state.goalsReached[d].isNullOrEmpty() -> GoalStar.Gold // reached beats due on the same day
                            !state.goalsDue[d].isNullOrEmpty() -> GoalStar.Silver
                            else -> null
                        },
                        onClick = { onDayClick(d) }, modifier = Modifier.weight(1f).testTag("day-$d"),
                    )
                }
            }
        }
    }
}

private val MARKER_SIZE = 10.dp
private val CELL_SHAPE = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)

/** Soft destructive wash of a scheduled-but-skipped day (circle behind the number + summary swatch). */
@Composable
private fun missedTint(): Color = LhTheme.colors.destructive.copy(alpha = if (LhTheme.colors.isDark) 0.16f else 0.10f)

/** Goal star of a day: silver = active goal due (deadline today or later), gold = goal reached that day. */
internal enum class GoalStar(val fill: Color, val outline: Color) {
    Silver(Color(0xFFA8B0BA), Color(0xFF6E7782)),
    Gold(Color(0xFFE8B923), Color(0xFF9A7209)),
}

/** Day number on a star: dark in both themes (the metallic fills are light). */
private val ON_STAR = Color(0xFF26231E)
private val STAR_SIZE = 38.dp

/** Filled five-point star with a darker outline (rounded joins); a plump inner radius leaves room for the day number. */
@Composable
private fun GoalStarShape(star: GoalStar, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.06f
        val outer = this.size.minDimension / 2f - stroke
        val inner = outer * 0.55f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f + outer * 0.09f // optical center: the star's mass sits low
        val path = androidx.compose.ui.graphics.Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + r * kotlin.math.cos(a).toFloat(); val y = cy + r * kotlin.math.sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, star.fill)
        drawPath(path, star.outline, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * Day number with its background, by priority:
 * star (gold > silver) is drawn on top and replaces the missed red circle; the selected fill / today ring stay
 * behind it, visible between the star's points. Below: tiny centered markers (trained, protein missed, cheat day).
 */
@Composable
private fun DayCell(
    d: LocalDate, inMonth: Boolean, isToday: Boolean, selected: Boolean, flags: DayFlags?, trained: Boolean, missed: Boolean,
    star: GoalStar?, onClick: () -> Unit, modifier: Modifier,
) {
    val c = LhTheme.colors
    val showMissed = missed && star == null
    Column(
        modifier.height(CELL_HEIGHT).padding(horizontal = 2.dp, vertical = 1.dp).clip(CELL_SHAPE)
            .alpha(if (inMonth) 1f else 0.35f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(STAR_SIZE), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(when { selected -> c.primary; showMissed -> missedTint(); else -> Color.Transparent })
                    .then(if (showMissed) Modifier.testTag("missed-$d") else Modifier)
                    .then(if (isToday && !selected) Modifier.border(1.5.dp, c.primary, CircleShape) else Modifier),
            )
            if (star != null) {
                // Always full size: the selected fill / today ring behind it shows between the star's points.
                GoalStarShape(star, STAR_SIZE, Modifier.testTag("star-${star.name.lowercase()}-$d"))
            }
            Text(
                d.dayOfMonth.toString(),
                style = LhType.tnum(if (star != null) LhType.caption else LhType.body).copy(
                    fontWeight = if (selected || isToday || star != null) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                ),
                color = when {
                    star != null -> ON_STAR
                    selected -> c.primaryForeground
                    showMissed -> c.destructive
                    else -> c.foreground
                },
                modifier = if (star != null) Modifier.padding(top = 2.dp) else Modifier,
            )
        }
        Row(Modifier.height(MARKER_SIZE + 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (trained) MetricIcon(MetricKind.Training, MARKER_SIZE, Modifier.testTag("flag-training-$d"))
            if (flags?.proteinMissed == true) MetricIcon(MetricKind.Protein, MARKER_SIZE, Modifier.testTag("flag-protein-$d"))
            if (flags?.kcalExceeded == true) MetricIcon(MetricKind.Calories, MARKER_SIZE, Modifier.testTag("flag-kcal-$d"), tint = c.destructive)
        }
    }
}

/** One compact line for the visible month; doubles as the legend of the day styling/markers. Hidden for future months. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthSummaryRow(
    s: MonthSummary, dueGoals: List<com.lifehub.app.data.model.Goal>, reachedGoals: List<com.lifehub.app.data.model.Goal>, modifier: Modifier = Modifier,
) {
    if (s.elapsedDays == 0 && dueGoals.isEmpty() && reachedGoals.isEmpty()) return
    val c = LhTheme.colors
    FlowRow(
        modifier.fillMaxWidth().testTag("month-summary"),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        @Composable fun item(icon: @Composable () -> Unit, text: String) =
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                icon(); Text(text, style = LhType.captionMuted)
            }
        // Goal deadlines of the month: one short item ("Muscle Up due 26 Nov"), or a count when several.
        if (dueGoals.isNotEmpty()) {
            val g = dueGoals.first()
            item({ GoalStarShape(GoalStar.Silver, 16.dp) },
                if (dueGoals.size == 1) stringResource(R.string.cal_goal_due, g.name, g.deadline.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)))
                else LocalContext.current.resources.getQuantityString(R.plurals.cal_goals_due, dueGoals.size, dueGoals.size))
        }
        if (reachedGoals.isNotEmpty()) {
            item({ GoalStarShape(GoalStar.Gold, 16.dp) }, LocalContext.current.resources.getQuantityString(R.plurals.cal_goals_reached, reachedGoals.size, reachedGoals.size))
        }
        if (s.elapsedDays == 0) Unit
        else if (s.allZero) {
            item({ Icon(Icons.Outlined.Check, null, tint = c.success, modifier = Modifier.size(14.dp)) }, stringResource(R.string.cal_on_track))
        } else {
            val res = LocalContext.current.resources
            // Same soft red wash as a missed day cell.
            if (s.trainingMissed > 0) item({ Box(Modifier.size(12.dp).clip(CircleShape).background(missedTint())) },
                res.getQuantityString(R.plurals.cal_sum_training, s.trainingMissed, s.trainingMissed))
            if (s.proteinMissed > 0) item({ MetricIcon(MetricKind.Protein, 14.dp) }, res.getQuantityString(R.plurals.cal_sum_protein, s.proteinMissed, s.proteinMissed))
            if (s.kcalExceeded > 0) item({ MetricIcon(MetricKind.Calories, 14.dp, tint = c.destructive) }, res.getQuantityString(R.plurals.cal_sum_kcal, s.kcalExceeded, s.kcalExceeded))
        }
    }
}

/** Inline details of the selected day: summary, food, workouts (collapsible), body. Stateless except for expansion. */
@Composable
fun DayDetail(
    day: DaySummary, today: LocalDate, proteinTarget: Int, onNavigate: (String) -> Unit,
    initiallyExpanded: Set<String> = emptySet(), modifier: Modifier = Modifier,
    onDeleteFood: (com.lifehub.app.data.model.FoodEntry) -> Unit = {},
    kcalTarget: Int? = null, carbsTarget: Int? = null, fatTarget: Int? = null,
    onCopyYesterday: (LocalDate) -> Unit = {}, onClearDay: (LocalDate) -> Unit = {},
    goalsDue: List<com.lifehub.app.data.model.Goal> = emptyList(),
    goalsReached: List<com.lifehub.app.data.model.Goal> = emptyList(),
    onDeleteQuickAdd: (com.lifehub.app.data.model.QuickAddEntry) -> Unit = {},
) {
    val c = LhTheme.colors
    var foodMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(day.date) { mutableStateOf(initiallyExpanded.toList()) }
    val future = day.date.isAfter(today)
    Column(modifier.fillMaxWidth()) {
        Text(
            day.date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)),
            style = LhType.title, color = c.foreground, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp).testTag("day-title"),
        )

        // Goals reached / due on this day, tappable to the goal detail.
        val goalRows = goalsReached.map { it to GoalStar.Gold } + goalsDue.map { it to GoalStar.Silver }
        if (goalRows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                goalRows.forEach { (g, star) ->
                    LhListItem(
                        g.name, subtitle = stringResource(if (star == GoalStar.Gold) R.string.cal_goal_reached_day else R.string.cal_goal_due_day),
                        leading = { GoalStarShape(star, 32.dp) },
                        trailing = { Icon(Icons.Outlined.ChevronRight, null, tint = c.mutedForeground) },
                        onClick = { onNavigate(Routes.goal(g.id)) }, modifier = Modifier.testTag("goal-${star.name.lowercase()}-row-${g.id}"),
                    )
                }
            }
        }

        // Summary: totals = food entries + quick adds (known values only; "—" when a macro is unknown for every item).
        val m = day.nutrition?.asMacroDay()
        LhCard(Modifier.fillMaxWidth().testTag("day-summary")) {
            val kcal = m?.kcal ?: 0
            LhKcalChip(if (kcalTarget != null && kcalTarget > 0) "${fmtInt(kcal)} / ${fmtInt(kcalTarget)} kcal" else "${fmtInt(kcal)} kcal")
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                fun ring(v: Int, known: Boolean, t: Int?, unit: String): String {
                    val shown = if (known) "$v" else "\u2014"
                    return if (t != null && t > 0) "$shown/$t $unit" else "$shown $unit"
                }
                fun prog(v: Int, t: Int?) = if (t != null && t > 0) v / t.toFloat() else 0f
                LhMacroRingStat(ring(m?.carbsG ?: 0, m?.carbsKnown ?: true, carbsTarget, "g"), "carbs", prog(m?.carbsG ?: 0, carbsTarget), c.carbs)
                LhMacroRingStat(ring(m?.fatG ?: 0, m?.fatKnown ?: true, fatTarget, "g"), "fat", prog(m?.fatG ?: 0, fatTarget), c.fat)
                LhMacroRingStat(ring(m?.proteinG ?: 0, m?.proteinKnown ?: true, proteinTarget, "g"), "protein", prog(m?.proteinG ?: 0, proteinTarget), c.protein)
            }
        }

        val entries = day.nutrition?.entries.orEmpty()
        val quick = day.quickAdds
        val hasFood = entries.isNotEmpty() || quick.isNotEmpty()
        LhSectionHeader("Food", icon = Icons.Outlined.Restaurant, action = {
            androidx.compose.foundation.layout.Box {
                com.lifehub.app.core.designsystem.components.LhIconAction(Icons.Outlined.MoreVert, "Food options", { foodMenu = true }, Modifier.testTag("food-menu"))
                androidx.compose.material3.DropdownMenu(foodMenu, { foodMenu = false }, containerColor = c.card) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Copy yesterday", color = c.foreground) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, tint = c.foreground) },
                        enabled = !future, onClick = { foodMenu = false; onCopyYesterday(day.date) },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Clear day", color = if (hasFood) c.destructive else c.mutedForeground) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = if (hasFood) c.destructive else c.mutedForeground) },
                        enabled = hasFood, onClick = { foodMenu = false; confirmClear = true },
                    )
                }
            }
        })
        if (confirmClear) com.lifehub.app.core.designsystem.components.LhConfirmDialog(
            "Clear day?", "Removes all food and quick-add macros logged on this day. You can undo right after.", "Clear day",
            onConfirm = { confirmClear = false; onClearDay(day.date) }, onDismiss = { confirmClear = false },
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("food-entries")) {
            // Foods and quick adds in one list, in the order they were logged.
            val rows = entries.map { it.createdAt to (it as Any) } + quick.map { it.createdAt to (it as Any) }
            rows.sortedBy { it.first }.forEach { (_, item) ->
                when (item) {
                    is com.lifehub.app.data.model.FoodEntry -> androidx.compose.runtime.key(item.id) {
                        SwipeToDeleteEntry(onDelete = { onDeleteFood(item) }) {
                            FoodEntryRow(item, { onNavigate(Routes.foodDetail(item.foodId ?: "none", item.date, item.id)) }, Modifier.testTag("food-entry-${item.id}"))
                        }
                    }
                    is com.lifehub.app.data.model.QuickAddEntry -> androidx.compose.runtime.key(item.id) {
                        SwipeToDeleteEntry(onDelete = { onDeleteQuickAdd(item) }) {
                            com.lifehub.app.feature.food.QuickAddRow(item, { onNavigate(Routes.macroEdit(day.date, item.id)) }, Modifier.testTag("quick-add-${item.id}"))
                        }
                    }
                }
            }
            if (!hasFood) Text("Nothing logged", style = LhType.captionMuted, modifier = Modifier.padding(start = 4.dp))
            if (!future) LhAddRow({ onNavigate(Routes.foodAdd(day.date)) }, Modifier.testTag("add-food"), contentDescription = "Add food")
            LhAddRow({ onNavigate(Routes.macroEdit(day.date)) }, Modifier.testTag("macros-row"), label = "Quick add")
        }

        LhSectionHeader(stringResource(R.string.cal_workouts), icon = Icons.Outlined.FitnessCenter)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            day.workouts.forEach { w ->
                val open = w.sessionId in expanded
                WorkoutCardView(w, open, onToggle = { expanded = if (open) expanded - w.sessionId else expanded + w.sessionId }, onOpen = { onNavigate(Routes.session(w.sessionId)) })
            }
            if (!future) LhAddRow({ onNavigate(Routes.workoutLog(day.date)) }, Modifier.testTag("add-workout"), label = "Log workout")
        }

        LhSectionHeader(stringResource(R.string.cal_body), icon = Icons.Outlined.MonitorWeight)
        val b = day.body
        if (b != null) {
            LhListItem(
                "${fmtKg(b.weightKg)} kg" + (b.fatKg?.let { " · ${fmtKg(fatPct(b))} % bf" } ?: ""),
                subtitle = b.muscleKg?.let { "muscle ${fmtKg(it)} kg" }, icon = Icons.Outlined.MonitorWeight, iconTint = c.carbs,
                trailing = { Icon(Icons.Outlined.ChevronRight, null, tint = c.mutedForeground) },
                onClick = { onNavigate(Routes.bodyEdit(day.date)) }, modifier = Modifier.testTag("body-row"),
            )
        } else {
            LhAddRow({ onNavigate(Routes.bodyEdit(day.date)) }, Modifier.testTag("body-row"), label = stringResource(R.string.cal_log_weight))
        }
    }
}

@Composable
private fun WorkoutCardView(w: WorkoutCard, open: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = LhTheme.colors
    LhCollapsibleCard(
        w.planName, open, onToggle, Modifier.testTag("workout-card-${w.sessionId}"),
        subtitle = listOfNotNull(w.durationSec?.let { fmtDuration(it) }, w.rpe?.let { "RPE $it" }).joinToString(" · ").ifEmpty { null },
        leading = { if (w.dayType != null) LhIconSquare(w.dayType) else LhIconSquare(Icons.Outlined.FitnessCenter) },
        trailing = {
            w.compliance?.let { k ->
                Column(horizontalAlignment = Alignment.End) {
                    Text("${k.percent}%", style = LhType.tnum(LhType.stat), color = complianceColor(k.percent))
                    Text(stringResource(R.string.cal_exercises_done, k.exercisesDone, k.exercisesTotal), style = LhType.tnum(LhType.captionMuted))
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.HorizontalDivider(color = c.border)
            w.exercises.forEach { ex ->
                val done = ex.sets.all { it.status == SetStatus.DONE }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ex.name, style = LhType.body, color = c.foreground, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (done) Icon(Icons.Outlined.Check, null, tint = c.success, modifier = Modifier.size(16.dp))
                    }
                    Text(ex.sets.joinToString("  ·  ") { setActualVsTarget(it) }, style = LhType.tnum(LhType.captionMuted))
                }
            }
            LhButton(stringResource(R.string.cal_open_session), onOpen, Modifier.fillMaxWidth(), variant = LhButtonVariant.Tonal, size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.OpenInNew)
        }
    }
}

@Composable
private fun complianceColor(pct: Int): Color = when {
    pct >= 90 -> LhTheme.colors.success
    pct >= 60 -> LhTheme.colors.foreground
    else -> LhTheme.colors.warning
}

/** "6/8" (actual/target), "7/MAX", "30/40 s", "skip". */
internal fun setActualVsTarget(s: SessionSet): String = when (s.status) {
    SetStatus.SKIPPED -> "skip"
    SetStatus.PENDING -> "–"
    SetStatus.DONE -> {
        val w = s.weightKg?.takeIf { it > 0 }?.let { " +${fmtWeight(it)}kg" }.orEmpty()
        when (s.targetType) {
            TargetType.SECONDS -> "${s.actualSeconds ?: 0}/${s.targetValue ?: "–"} s"
            TargetType.MAX -> "${s.actualReps ?: 0}/MAX"
            TargetType.REPS -> "${s.actualReps ?: 0}/${s.targetValue ?: "–"}"
        } + w
    }
}
