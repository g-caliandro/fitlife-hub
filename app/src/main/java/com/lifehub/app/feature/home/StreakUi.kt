package com.lifehub.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lifehub.app.core.designsystem.LhMotion
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonSize
import com.lifehub.app.domain.StreakDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Streak flame orange (same in light and dark: it is a "brand" accent like Duolingo's). */
val StreakOrange = Color(0xFFFF8A00)

/** Home header badge: flame + number (grey flame at 0). The only place the streak is shown. Tap -> [StreakCelebration] on demand. */
@Composable
fun StreakBadge(days: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val on = days > 0
    Row(
        modifier.clip(LhRadius.fullShape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp).testTag("home-streak"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.LocalFireDepartment, "Training streak", Modifier.size(24.dp), tint = if (on) StreakOrange else c.mutedForeground.copy(alpha = 0.5f))
        Text("$days", style = LhType.tnum(LhType.heading), color = if (on) StreakOrange else c.mutedForeground)
    }
}

/**
 * Streak sheet with the last 7 days. [extended] = the once-a-day "streak extended" celebration (after the workout
 * that extended it): the flame pops in, the number rolls from yesterday's value to today's. Otherwise it is opened on
 * demand from the badge: no roll, neutral copy, and a grey flame with an encouraging line at 0.
 */
@Composable
fun StreakCelebration(days: Int, week: List<Pair<LocalDate, StreakDay>>, extended: Boolean = true, onDismiss: () -> Unit) {
    val c = LhTheme.colors
    val reduced = LhMotion.reduced
    val on = days > 0
    val pop = remember { Animatable(if (reduced) 1f else 0f) }
    var shown by remember { mutableIntStateOf(if (reduced || !extended) days else (days - 1).coerceAtLeast(0)) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
            delay(550)
            shown = days
        }
    }
    val flame = if (on) StreakOrange else c.mutedForeground.copy(alpha = 0.5f)
    val wobble by rememberInfiniteTransition(label = "flame").animateFloat(
        -4f, 4f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "flame-rot",
    )
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier.fillMaxSize().background(c.background).padding(horizontal = 24.dp).testTag("streak-celebration"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(contentAlignment = Alignment.Center) {
                    if (on) Sparks(pop.value)
                    Icon(
                        Icons.Filled.LocalFireDepartment, null,
                        Modifier.size(150.dp).scale(pop.value).rotate(if (reduced || !on) 0f else wobble), tint = flame,
                    )
                }
                AnimatedContent(
                    shown, label = "streak-count",
                    transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                ) { n ->
                    Text("$n", style = LhType.tnum(LhType.display.copy(fontSize = 88.sp, lineHeight = 96.sp)), color = if (on) StreakOrange else c.mutedForeground, modifier = Modifier.testTag("streak-count"))
                }
                Text(if (!on) "No streak yet" else if (extended) "day streak!" else "day streak", style = LhType.title, color = c.foreground)
                Text(
                    when {
                        !on -> "Finish a workout to light the flame. Rest days keep it alive; skipped a planned workout? Make it up the next day."
                        extended -> "Workout done. Rest days keep the flame alive. Skipped a planned workout? Make it up the next day."
                        else -> "Rest days keep the flame alive. Skipped a planned workout? Make it up the next day."
                    },
                    style = LhType.body, color = c.mutedForeground, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(28.dp))
                WeekStrip(week)
            }
            LhButton(if (on) "Keep it up" else "Got it", onDismiss, Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp).testTag("streak-continue"), size = LhButtonSize.Lg)
        }
    }
}

@Composable
private fun Sparks(progress: Float) {
    if (progress <= 0f || progress >= 0.999f) return
    Canvas(Modifier.size(220.dp)) {
        val r = size.minDimension / 2 * progress
        for (k in 0 until 10) {
            val a = Math.toRadians(36.0 * k - 90)
            drawCircle(StreakOrange.copy(alpha = (1f - progress).coerceIn(0f, 1f)), 5.dp.toPx(), center + Offset((Math.cos(a) * r).toFloat(), (Math.sin(a) * r).toFloat()))
        }
    }
}

/** Last 7 days: orange flame = trained, soft orange check = rest / made-up day, grey = pending, missed or before the streak. */
@Composable
fun WeekStrip(week: List<Pair<LocalDate, StreakDay>>) {
    val c = LhTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.card, LhRadius.cardShape).padding(vertical = 14.dp, horizontal = 8.dp).testTag("streak-week"),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        week.forEach { (d, s) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2), style = LhType.caption, color = c.mutedForeground)
                val (bg, fg) = when (s) {
                    StreakDay.TRAINED -> StreakOrange to Color.White
                    StreakDay.REST, StreakDay.FORGIVEN -> StreakOrange.copy(alpha = 0.22f) to StreakOrange
                    else -> c.track to c.mutedForeground
                }
                Box(Modifier.size(32.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                    when (s) {
                        StreakDay.TRAINED -> Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(18.dp), tint = fg)
                        StreakDay.REST, StreakDay.FORGIVEN -> Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = fg)
                        else -> {}
                    }
                }
            }
        }
    }
}
