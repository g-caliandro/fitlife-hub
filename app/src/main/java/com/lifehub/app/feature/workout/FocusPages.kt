package com.lifehub.app.feature.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.border
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifehub.app.core.designsystem.ExerciseIcon
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.core.designsystem.fmtClock
import com.lifehub.app.core.designsystem.fmtWeight
import com.lifehub.app.data.model.SessionSet
import com.lifehub.app.data.model.SetStatus
import com.lifehub.app.data.model.TargetType

/** One bar segment per set, grouped by exercise; done filled, skipped striped/muted, current pulsing. */
@Composable
fun SegmentedProgress(groups: List<List<Seg>>, nowMillis: Long, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pulse-alpha",
    )
    Row(modifier.fillMaxWidth().height(6.dp).testTag("focus-progress"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { g ->
            Row(Modifier.weight(g.size.toFloat()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                g.forEach { s ->
                    val shape = LhRadius.fullShape
                    val m = Modifier.weight(1f).height(6.dp).clip(shape)
                    when {
                        s.current -> Box(m.background(c.track).background(c.primary.copy(alpha = pulse)))
                        s.status == SetStatus.DONE -> Box(m.background(c.primary))
                        s.status == SetStatus.SKIPPED -> Box(
                            m.background(c.mutedForeground.copy(alpha = 0.35f)).drawBehind {
                                var x = -size.height
                                while (x < size.width) {
                                    drawLine(c.mutedForeground.copy(alpha = 0.55f), Offset(x, size.height), Offset(x + size.height, 0f), 1.5f)
                                    x += 5.dp.toPx()
                                }
                            },
                        )
                        else -> Box(m.background(c.track))
                    }
                }
            }
        }
    }
}

@Composable
private fun CountRing(progress: Float, color: Color, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val track = LhTheme.colors.track
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 12.dp.toPx()
            val inset = w / 2
            val sz = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Done burst: primary check circle springs 0 -> 1.15 -> 1 with 8 dots radiating outwards (450 ms) each time [trigger] increases. */
@Composable
private fun CheckBurst(trigger: Int, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    val scale = remember { Animatable(0f) }
    val dots = remember { Animatable(1f) }
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger > 0 && !com.lifehub.app.core.designsystem.LhMotion.reduced) {
            active = true
            scale.snapTo(0f); dots.snapTo(0f)
            launch { scale.animateTo(1f, androidx.compose.animation.core.spring(0.5f, 400f)) }
            dots.animateTo(1f, tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            active = false
        }
    }
    if (active) {
        Box(modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = 56.dp.toPx() * dots.value
                for (k in 0 until 8) {
                    val a = Math.toRadians(45.0 * k)
                    drawCircle(c.primary.copy(alpha = 1f - dots.value), 4.dp.toPx(), center + Offset((Math.cos(a) * r).toFloat(), (Math.sin(a) * r).toFloat()))
                }
            }
            Box(Modifier.size(72.dp).scale(scale.value).background(c.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, null, Modifier.size(40.dp), tint = c.primaryForeground)
            }
        }
    }
}

private fun targetText(set: SessionSet, input: SetInput): String {
    val v = input.value.ifBlank { set.targetValue?.toString().orEmpty() }
    return when (set.targetType) {
        TargetType.MAX -> "MAX"
        TargetType.SECONDS -> if (v.isEmpty()) "—" else "$v s"
        TargetType.REPS -> if (v.isEmpty()) "—" else if (v == "1") "1 rep" else "$v reps"
    }
}

@Composable
fun SetPage(
    ui: LiveUi, set: SessionSet, nowMillis: Long, hold: Boolean, burst: Int, busy: Boolean, a: LiveActions,
    onDone: (SetInput?) -> Unit,
) {
    val c = LhTheme.colors
    val raw = if (busy) WorkoutOps.prefill(set) else ui.input
    // A picker always shows a number: a blank value (MAX without history) starts from a sensible default.
    val input = if (raw.value.isBlank()) raw.copy(value = WorkoutOps.fallbackValue(set, ui.sets, null)) else raw
    val canDone = WorkoutOps.canComplete(input)
    val block = ui.sets.filter { it.exerciseOrder == set.exerciseOrder }
    val prev = ui.previous[set.id]?.takeIf { it != "—" }
    val seconds = set.targetType == TargetType.SECONDS

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                ExerciseIcon(set.exerciseName, 140.dp, dayType = ui.dayType)
                CheckBurst(burst)
            }
            Text(set.exerciseName, style = LhType.screenTitle, color = c.foreground, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp).testTag("focus-title"))
            Text("Set ${set.setIndex + 1} of ${block.size}", style = LhType.bodyLarge, color = c.mutedForeground, modifier = Modifier.padding(top = 2.dp))
            if (hold) {
                val start = ui.holdStartMillis ?: nowMillis
                val el = ((nowMillis - start) / 1000).toInt().coerceAtLeast(0)
                val tgt = set.targetValue
                val shown = if (tgt != null && tgt > 0) (tgt - el).coerceAtLeast(0) else el
                CountRing(if (tgt != null && tgt > 0) el.toFloat() / tgt else 0f, c.primary, 190.dp, Modifier.padding(top = 20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmtClock(shown), style = LhType.tnum(LhType.timer.copy(fontSize = 48.sp)), color = c.foreground)
                        Text(if (tgt != null && tgt > 0) "of ${tgt} s" else "holding", style = LhType.caption, color = c.mutedForeground)
                    }
                }
            } else {
                Text(
                    "Target ${targetText(set, SetInput("", ""))}" + (set.weightKg?.takeIf { it > 0 }?.let { " · +${fmtWeight(it)} kg" } ?: ""),
                    style = LhType.body, color = c.mutedForeground, modifier = Modifier.padding(top = 2.dp).testTag("focus-target"),
                )
                // Compact scroll pickers, prefilled with the target (or the plan's remembered progress).
                key(set.id) {
                    val pre = WorkoutOps.prefill(set)
                    // Off-grid numbers that must stay exactly selectable: target, prefill, the value the page opened with.
                    val first = remember { input }
                    val planned = maxOf(set.targetValue ?: 0, pre.value.toIntOrNull() ?: 0, first.value.toIntOrNull() ?: 0).toDouble()
                    val kgValues = remember {
                        val w = listOf(set.weightKg, pre.weight.toDoubleOrNull(), first.weight.toDoubleOrNull())
                        WorkoutOps.pickerValues(0.5, maxOf(300.0, (w.filterNotNull().maxOrNull() ?: 0.0) * 2), w)
                    }
                    val countValues = remember {
                        val v = listOf(set.targetValue?.toDouble(), pre.value.toDoubleOrNull(), first.value.toDoubleOrNull())
                        if (seconds) WorkoutOps.pickerValues(5.0, maxOf(600.0, planned * 2).coerceAtMost(999.0), v)
                        else WorkoutOps.pickerValues(1.0, maxOf(100.0, planned * 2).coerceAtMost(999.0), v)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)) {
                        LhWheelPicker(
                            countValues, input.value.toDoubleOrNull() ?: 0.0, { a.onValue(it.toInt().toString()) },
                            if (seconds) "Seconds" else "Reps", Modifier.width(132.dp), unit = if (seconds) "s" else null,
                        )
                        LhWheelPicker(
                            kgValues, input.weight.toDoubleOrNull() ?: 0.0, { a.onWeight(WorkoutOps.weightInput(it)) },
                            "Weight", Modifier.width(132.dp), unit = "kg", format = { if (it <= 0.0) "0" else fmtWeight(it) },
                        )
                    }
                }
            }
            if (prev != null) Text("Last time: $prev", style = LhType.body, color = c.mutedForeground, modifier = Modifier.padding(top = 12.dp).testTag("set-prev"))
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val done = { onDone(if (raw.value.isBlank()) input else null) }
            when {
                hold -> {
                    LhButton("Stop & Done", { a.onFinishHold(false) }, Modifier.fillMaxWidth(), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.Stop)
                    LhButton("Cancel hold", a.onCancelHold, Modifier.fillMaxWidth(), LhButtonVariant.Tonal, LhButtonSize.Md)
                }
                seconds -> {
                    LhButton("Start hold", a.onStartHold, Modifier.fillMaxWidth(), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.PlayArrow, enabled = !busy)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LhButton("Skip", a.onSkipSet, Modifier.weight(1f), LhButtonVariant.Outline, LhButtonSize.Md, Icons.Outlined.SkipNext, enabled = !busy)
                        LhButton("Done", done, Modifier.weight(1f).testTag("focus-done"), LhButtonVariant.Outline, LhButtonSize.Md, Icons.Outlined.Check, enabled = !busy && canDone)
                    }
                }
                else -> {
                    LhButton("Done", done, Modifier.fillMaxWidth().testTag("focus-done"), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.Check, enabled = !busy && canDone)
                    LhButton("Skip", a.onSkipSet, Modifier.fillMaxWidth(), LhButtonVariant.Outline, LhButtonSize.Md, Icons.Outlined.SkipNext, enabled = !busy)
                }
            }
        }
    }
}

@Composable
private fun UpNextCard(ui: LiveUi, set: SessionSet) {
    val c = LhTheme.colors
    val block = ui.sets.count { it.exerciseOrder == set.exerciseOrder }
    val target = targetText(set, WorkoutOps.prefill(set))
    val w = set.weightKg?.takeIf { it > 0 }?.let { " · +${fmtWeight(it)} kg" }.orEmpty()
    Row(
        Modifier.fillMaxWidth().background(c.card, LhRadius.cardShape).border(1.dp, c.border, LhRadius.cardShape).padding(12.dp).testTag("up-next"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExerciseIcon(set.exerciseName, 48.dp, dayType = ui.dayType)
        Column(Modifier.weight(1f)) {
            Text("Up next", style = LhType.caption, color = c.mutedForeground)
            Text(set.exerciseName, style = LhType.heading, color = c.foreground, maxLines = 1)
            Text("Set ${set.setIndex + 1} of $block · $target$w", style = LhType.body, color = c.mutedForeground, maxLines = 1)
        }
    }
}

@Composable
fun RestPage(ui: LiveUi, next: SessionSet, nowMillis: Long, restDone: Boolean, onAdjust: (Int) -> Unit, onSkipRest: () -> Unit) {
    val c = LhTheme.colors
    val remaining = ui.restEndsAtMillis?.let { ((it - nowMillis + 999) / 1000).toInt() }?.coerceAtLeast(0) ?: 0
    val total = ui.restTotalSec.coerceAtLeast(1)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (restDone) {
                CountRing(1f, c.primary, 220.dp) { Icon(Icons.Outlined.Check, null, Modifier.size(56.dp), tint = c.primary) }
                Text("Ready — next set", style = LhType.screenTitle, color = c.foreground, modifier = Modifier.padding(top = 20.dp))
            } else {
                CountRing(remaining.toFloat() / total, if (remaining <= 10) c.warning else c.primary, 220.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmtClock(remaining), style = LhType.tnum(LhType.timer.copy(fontSize = 56.sp, lineHeight = 64.sp)), color = c.foreground, modifier = Modifier.testTag("rest-time"))
                        Text("Rest", style = LhType.body, color = c.mutedForeground)
                    }
                }
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LhButton("-15 s", { onAdjust(-15) }, variant = LhButtonVariant.Outline, size = LhButtonSize.Md)
                    LhButton("+15 s", { onAdjust(15) }, variant = LhButtonVariant.Outline, size = LhButtonSize.Md)
                }
            }
            Spacer(Modifier.height(24.dp))
            UpNextCard(ui, next)
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 16.dp)) {
            if (restDone) LhButton("Start next set", onSkipRest, Modifier.fillMaxWidth(), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.PlayArrow)
            else LhButton("Skip rest", onSkipRest, Modifier.fillMaxWidth(), LhButtonVariant.Tonal, size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.SkipNext)
        }
    }
}

@Composable
fun FinishedPage(ui: LiveUi, onFinish: () -> Unit, onOverview: () -> Unit) {
    val c = LhTheme.colors
    val s = WorkoutOps.summarize(ui.sets)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(96.dp).background(c.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, null, Modifier.size(48.dp), tint = c.primaryForeground)
            }
            Text("All sets done", style = LhType.screenTitle, color = c.foreground, modifier = Modifier.padding(top = 20.dp))
            Text(
                "${s.done} done" + (if (s.skipped > 0) " · ${s.skipped} skipped" else "") + (if (s.reps > 0) " · ${s.reps} reps" else "") +
                    (if (s.holdSeconds > 0) " · ${s.holdSeconds} s held" else ""),
                style = LhType.bodyLarge, color = c.mutedForeground, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LhButton("Finish workout", onFinish, Modifier.fillMaxWidth(), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.Check)
            LhButton("Review sets", onOverview, Modifier.fillMaxWidth(), LhButtonVariant.Tonal, LhButtonSize.Lg)
        }
    }
}
