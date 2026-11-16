package com.lifehub.app.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhIconButton
import com.lifehub.app.core.designsystem.dayType
import com.lifehub.app.data.model.DayType
import com.lifehub.app.data.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Monday-aligned weeks covering [month] (leading / trailing days of the neighbour months included). */
internal fun planMonthWeeks(month: YearMonth): List<List<LocalDate>> {
    val first = month.atDay(1).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
    val last = month.atEndOfMonth().let { it.plusDays((7 - it.dayOfWeek.value).toLong()) }
    return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList().chunked(7)
}

/**
 * Compact month calendar of one plan's completed sessions (Diary look): a day with a session is a filled circle
 * in the plan's day-type colour (primary when the plan has none) and opens that session (the latest of the day).
 */
@Composable
fun PlanSessionsCalendar(
    sessions: List<WorkoutSession>, today: LocalDate, dayType: DayType?, onSession: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val c = LhTheme.colors
    val fill = dayType?.let { c.dayType(it) } ?: c.primary
    val byDay = remember(sessions) { sessions.groupBy { it.date }.mapValues { (_, l) -> l.maxBy { it.startedAt } } }
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthKey)
    val inMonth = byDay.keys.count { YearMonth.from(it) == month }
    LhCard(modifier.fillMaxWidth().testTag("plan-calendar"), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LhIconButton(Icons.Outlined.ChevronLeft, "Previous month", { monthKey = month.minusMonths(1).toString() })
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year,
                    style = LhType.heading, color = c.foreground, textAlign = TextAlign.Center, modifier = Modifier.testTag("plan-calendar-month"),
                )
                Text(
                    when (inMonth) { 0 -> "No sessions"; 1 -> "1 session"; else -> "$inMonth sessions" },
                    style = LhType.captionMuted,
                )
            }
            LhIconButton(Icons.Outlined.ChevronRight, "Next month", { monthKey = month.plusMonths(1).toString() })
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
            for (i in 0 until 7) {
                Text(
                    DayOfWeek.MONDAY.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    style = LhType.captionMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        planMonthWeeks(month).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    val mine = YearMonth.from(d) == month
                    val session = if (mine) byDay[d] else null
                    val isToday = d == today
                    Box(
                        Modifier.weight(1f).height(38.dp).then(
                            if (session != null) Modifier.clickable { onSession(session.id) }.testTag("plan-session-$d") else Modifier,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (mine) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape)
                                    .background(if (session != null) fill else Color.Transparent)
                                    .then(if (isToday && session == null) Modifier.border(1.5.dp, c.primary, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    d.dayOfMonth.toString(),
                                    style = LhType.tnum(LhType.body).copy(fontWeight = if (session != null || isToday) FontWeight.SemiBold else FontWeight.Normal),
                                    color = if (session != null) Color.White else c.foreground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
