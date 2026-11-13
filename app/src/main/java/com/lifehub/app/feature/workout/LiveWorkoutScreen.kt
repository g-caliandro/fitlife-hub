package com.lifehub.app.feature.workout

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.container
import com.lifehub.app.core.audio.SoftChime
import com.lifehub.app.core.designsystem.KeepScreenOn
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.core.designsystem.fmtClock
import com.lifehub.app.core.ui.ExercisePickerSheet
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.navigation.LocalSnackbarHostState
import com.lifehub.app.navigation.Routes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Callbacks of the stateless live screen. */
class LiveActions(
    val onBack: () -> Unit = {},
    val onFocus: (Int) -> Unit = {},
    val onWeight: (String) -> Unit = {},
    val onValue: (String) -> Unit = {},
    val onDone: (SetInput?) -> Unit = {},
    val onSkipSet: () -> Unit = {},
    val onApplyWeight: () -> Unit = {},
    val onToggleSkip: (String) -> Unit = {},
    val onRemoveSet: (String) -> Unit = {},
    val onAddSet: (Int) -> Unit = {},
    val onAddExercise: (Exercise, Int, TargetType, Int?, Double?) -> Unit = { _, _, _, _, _ -> },
    val onSwapExercise: (Int, Exercise) -> Unit = { _, _ -> },
    val onCreateExercise: (Exercise) -> Unit = {},
    val onStartHold: () -> Unit = {},
    val onFinishHold: (auto: Boolean) -> Unit = {},
    val onCancelHold: () -> Unit = {},
    val onAdjustRest: (Int) -> Unit = {},
    val onSkipRest: () -> Unit = {},
    val onFinish: (String?, Int?) -> Unit = { _, _ -> },
    val onDiscard: () -> Unit = {},
    val onQuickDone: (String) -> Unit = {},
    val onRemoveExercise: (Int) -> Unit = {},
    val loadPrs: suspend () -> List<String> = { emptyList() },
)

@Composable
fun LiveWorkoutRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val vm: LiveWorkoutViewModel = viewModel(factory = LiveWorkoutViewModel.factory(container))
    val ui by vm.ui.collectAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    // Spec §4: Done = CONFIRM (API 30+, else LONG_PRESS); Skip = CLOCK_TICK. Kept under reduced motion.
    val confirm = { view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.LONG_PRESS) }
    val tick = { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val now by produceState(container.time.now().toEpochMilli()) {
        while (true) { value = container.time.now().toEpochMilli(); delay(250) }
    }

    LaunchedEffect(ui.missing) { if (ui.missing) onBack() }
    // Foreground tracking so the background rest alarm does not double-alert.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_START) com.lifehub.app.notifications.LiveWorkoutVisible.value = true
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) com.lifehub.app.notifications.LiveWorkoutVisible.value = false
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); com.lifehub.app.notifications.LiveWorkoutVisible.value = false }
    }
    // New PR: success haptic.
    LaunchedEffect(ui.pr?.id) { if (ui.pr != null) vibrate(context, longArrayOf(0, 40, 70, 90)) }

    // Rest end: soft chime + gentle vibration once, only if the end is reached while this screen is open.
    LaunchedEffect(ui.restEndsAtMillis) {
        val end = ui.restEndsAtMillis ?: return@LaunchedEffect
        val wait = end - container.time.now().toEpochMilli()
        if (wait < -1500) return@LaunchedEffect
        if (wait > 0) delay(wait)
        vibrate(context, longArrayOf(0, 90, 90, 90)); SoftChime.play()
    }
    // Isometric target reached: auto-complete the hold (logs the target) with a soft chime.
    val target = ui.focused?.takeIf { it.targetType == TargetType.SECONDS }?.targetValue
    LaunchedEffect(ui.holdStartMillis, target) {
        val start = ui.holdStartMillis ?: return@LaunchedEffect
        if (target == null || target <= 0) return@LaunchedEffect
        val wait = start + target * 1000L - container.time.now().toEpochMilli()
        if (wait > 0) delay(wait)
        if (vm.finishHold(true)) { vibrate(context, longArrayOf(0, 120)); SoftChime.play() }
    }

    val actions = LiveActions(
        onBack = onBack, onFocus = vm::focusIndex, onWeight = vm::onWeight, onValue = vm::onValue,
        onDone = { input -> if (vm.done(input)) confirm() },
        onSkipSet = { tick(); vm.skipFocused() }, onApplyWeight = vm::applyWeightToRemaining, onToggleSkip = vm::toggleSkip,
        onRemoveSet = { id ->
            vm.removeSet(id) { original ->
                scope.launch {
                    val r = snackbar.showSnackbar("Set removed", "Undo", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) vm.restore(original)
                }
            }
        },
        onQuickDone = { id -> if (vm.quickDone(id)) confirm() },
        onRemoveExercise = { order ->
            vm.removeExercise(order) { original ->
                scope.launch {
                    val r = snackbar.showSnackbar("Exercise removed", "Undo", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) vm.restore(original)
                }
            }
        },
        loadPrs = vm::sessionPrTitles,
        onAddSet = vm::addSet, onAddExercise = vm::addExercise, onSwapExercise = vm::swapExercise, onCreateExercise = vm::createExercise,
        onStartHold = vm::startHold, onFinishHold = { auto -> if (vm.finishHold(auto)) confirm() },
        onCancelHold = vm::cancelHold, onAdjustRest = vm::adjustRest, onSkipRest = vm::skipRest,
        onFinish = { notes, rpe ->
            vm.finish(notes, rpe) {
                onNavigate(Routes.home())
                container.appScope.launch(kotlinx.coroutines.Dispatchers.Main) { snackbar.showSnackbar("Workout saved") }
            }
        },
        onDiscard = { vm.discard { onBack() } },
    )
    if (ui.loaded && !ui.missing) LiveWorkoutScreen(ui, now, actions)
}

private fun vibrate(context: Context, pattern: LongArray) {
    try {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        else @Suppress("DEPRECATION") v.vibrate(pattern, -1)
    } catch (_: Exception) {}
}

/** Identity of the full-screen step; AnimatedContent slides when it changes. group: 0 set/hold, 1 rest, 2 finished. */
private data class StepKey(val group: Int, val setId: String?)


@Composable
fun LiveWorkoutScreen(ui: LiveUi, nowMillis: Long, a: LiveActions) {
    val c = LhTheme.colors
    var showFinish by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showOverview by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var burst by remember { mutableIntStateOf(0) }

    val step = FocusSteps.derive(ui.sets, ui.focusedIndex, ui.restEndsAtMillis, ui.holdStartMillis, nowMillis)
    val liveKey = StepKey(
        when (step.kind) { StepKind.SET, StepKind.HOLD_RUNNING -> 0; StepKind.REST, StepKind.REST_DONE -> 1; StepKind.FINISHED -> 2 },
        step.setIndex?.let { ui.sets[it].id },
    )
    // On Done the check burst plays on the finished set for a beat before sliding to the next step.
    var shownKey by remember { mutableStateOf(liveKey) }
    var acting by remember { mutableStateOf(false) }
    LaunchedEffect(liveKey) {
        if (acting && shownKey.group == 0 && liveKey != shownKey) delay(450)
        acting = false
        shownKey = liveKey
    }
    val elapsed = ((nowMillis - ui.startedAtMillis) / 1000).toInt().coerceAtLeast(0)
    val pos = FocusSteps.position(ui.sets, step.setIndex)

    fun done(input: SetInput?) { acting = true; burst++; a.onDone(input) }

    KeepScreenOn()
    Box(Modifier.fillMaxSize()) {
        val bg by androidx.compose.animation.animateColorAsState(
            if (step.kind == StepKind.REST_DONE) c.primaryContainer else c.background,
            com.lifehub.app.core.designsystem.lhTween(com.lifehub.app.core.designsystem.LhMotion.medium), label = "focus-bg",
        )
        Column(Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                LhIconButton(Icons.Outlined.Close, "Back", a.onBack)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        if (pos != null) "Exercise ${pos.exercise}/${pos.exercises} · Set ${pos.set}/${pos.sets}" else "Workout complete",
                        style = LhType.label, color = c.foreground, maxLines = 1,
                    )
                    Text(ui.planName, style = LhType.caption, color = c.mutedForeground, maxLines = 1)
                }
                Text(fmtClock(elapsed), style = LhType.tnum(LhType.label), color = c.mutedForeground, modifier = Modifier.padding(horizontal = 8.dp))
                LhIconButton(Icons.Outlined.FormatListBulleted, "Overview", { showOverview = true })
                Box {
                    LhIconButton(Icons.Outlined.MoreVert, "More", { menu = true })
                    DropdownMenu(menu, { menu = false }, containerColor = c.background) {
                        DropdownMenuItem(text = { Text("Finish workout") }, onClick = { menu = false; showFinish = true })
                        DropdownMenuItem(text = { Text("Discard workout", color = c.destructive) }, onClick = { menu = false; showDiscard = true })
                    }
                }
            }
            SegmentedProgress(FocusSteps.segments(ui.sets, step.setIndex), nowMillis, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            AnimatedContent(
                targetState = shownKey, modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { com.lifehub.app.core.designsystem.focusStepTransition(forward = true) },
                label = "focus-step",
            ) { key ->
                val set = key.setId?.let { id -> ui.sets.firstOrNull { it.id == id } }
                val live = set != null && step.setIndex != null && ui.sets[step.setIndex].id == set.id
                when {
                    key.group == 2 || set == null -> FinishedPage(ui, { showFinish = true }, { showOverview = true })
                    key.group == 1 -> RestPage(
                        ui, set, nowMillis, restDone = live && step.kind == StepKind.REST_DONE,
                        onAdjust = a.onAdjustRest, onSkipRest = a.onSkipRest,
                    )
                    else -> SetPage(
                        ui, set, nowMillis, hold = live && step.kind == StepKind.HOLD_RUNNING, burst = burst, busy = acting || !live, a = a,
                        onDone = ::done,
                    )
                }
            }
        }
        PrBannerOverlay(ui.pr, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 96.dp))
    }

    if (showOverview) LiveOverviewSheet(ui, step.setIndex, a, { showOverview = false })
    if (showFinish) {
        FinishSheet(WorkoutOps.summarize(ui.sets), (elapsed + 30) / 60, a.loadPrs, { n, r -> showFinish = false; a.onFinish(n, r) }, { showFinish = false })
    }
    if (showDiscard) {
        LhConfirmDialog("Discard workout?", "All sets logged in this session will be deleted.", "Discard",
            onConfirm = { showDiscard = false; a.onDiscard() }, onDismiss = { showDiscard = false })
    }
}

/** Minimal "New PR" pill: trophy + text, scale + fade in/out. */
@Composable
fun PrBannerOverlay(pr: PrBanner?, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    var last by remember { mutableStateOf(pr) }
    if (pr != null) last = pr
    AnimatedVisibility(
        visible = pr != null, modifier = modifier,
        enter = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.8f),
        exit = fadeOut(tween(400)) + scaleOut(tween(400), targetScale = 0.95f),
    ) {
        Row(
            Modifier.background(c.primary, LhRadius.fullShape).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(20.dp), tint = c.warning)
            Text(last?.text.orEmpty(), style = LhType.label, color = c.primaryForeground, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

