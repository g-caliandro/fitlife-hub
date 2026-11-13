package com.lifehub.app.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import com.lifehub.app.core.designsystem.ExerciseIcon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.DayTypeIcon
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.core.designsystem.fmtTarget
import com.lifehub.app.core.designsystem.fmtWeight
import com.lifehub.app.data.model.PlanWithExercises
import com.lifehub.app.data.model.TargetType
import com.lifehub.app.navigation.Routes

/** Item 17: pre-session preview of a plan; the only filled button starts the workout. */
@Composable
fun WorkoutPreviewRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: WorkoutStartViewModel = viewModel(factory = WorkoutStartViewModel.factory(context.container))
    val plan by vm.plan.collectAsState()
    val ui by vm.ui.collectAsState()
    val nav by vm.navigate.collectAsState()
    val start = rememberStarter(vm)
    LaunchedEffect(nav) { nav?.let { onNavigate(it) } }
    WorkoutPreviewScreen(
        plan, onBack, onStart = { plan?.let { start(it.plan.id) } },
    )
    if (ui.conflictPlanId != null) StartConflictDialog(ui.conflictPlanName, vm::resume, vm::discardAndStart, vm::dismissConflict)
}

@Composable
fun WorkoutPreviewScreen(plan: PlanWithExercises?, onBack: () -> Unit, onStart: () -> Unit) {
    val c = LhTheme.colors
    Column(Modifier.fillMaxSize()) {
        LhTopBar(plan?.plan?.name ?: "Workout", onBack = onBack, titleIcon = plan?.plan?.dayType)
        if (plan == null) {
            Column(Modifier.weight(1f)) {}
        } else {
            val sets = plan.exercises.sumOf { it.planExercise.sets }
            val minutes = (WorkoutOps.estimateSeconds(plan) + 30) / 60
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    LhCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (plan.plan.dayType != null) LhIconSquare(plan.plan.dayType!!, size = 56.dp)
                            else LhIconSquare(Icons.Outlined.FitnessCenter, size = 56.dp)
                            Column(Modifier.weight(1f)) {
                                Text("~$minutes min", style = LhType.tnum(LhType.stat), color = c.foreground)
                                Text("${plan.exercises.size} exercises · $sets sets", style = LhType.captionMuted)
                            }
                        }
                    }
                }
                items(plan.exercises, key = { it.planExercise.id }) { item ->
                    val pe = item.planExercise
                    val target = if (pe.targetType == TargetType.MAX) "MAX" else fmtTarget(pe.targetType, pe.targetValue)
                    val w = pe.weightKg?.takeIf { it > 0 }?.let { " · +${fmtWeight(it)} kg" }.orEmpty()
                    LhListItem(
                        item.exercise.name,
                        leading = { Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { ExerciseIcon(item.exercise, 40.dp, dayType = plan.plan.dayType) } },
                        trailingText = "${pe.sets} × $target$w",
                    )
                }
            }
        }
        LhBottomActionBar {
            LhButton("Start workout", onStart, Modifier.fillMaxWidth(), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.PlayArrow, enabled = plan != null)
        }
    }
}
