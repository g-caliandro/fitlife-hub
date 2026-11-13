package com.lifehub.app.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.data.model.Exercise
import com.lifehub.app.data.model.TargetType

/** Inline setup for an ad-hoc exercise: sets, type, value, weight (F-WO-7). Never touches the plan. */
@Composable
fun AddExerciseSheet(
    exercise: Exercise, onAdd: (sets: Int, type: TargetType, value: Int?, weightKg: Double?) -> Unit, onDismiss: () -> Unit,
) {
    val c = LhTheme.colors
    var sets by remember { mutableStateOf("3") }
    var type by remember { mutableStateOf(exercise.defaultTargetType) }
    var value by remember { mutableStateOf(if (exercise.defaultTargetType == TargetType.SECONDS) "30" else "8") }
    var weight by remember { mutableStateOf("") }
    val count = sets.toIntOrNull() ?: 0
    val v = value.toIntOrNull() ?: 0
    val valid = count in 1..20 && (type == TargetType.MAX || v >= 1)
    LhBottomSheet(onDismiss, title = exercise.name) {
        LhSegmentedControl(TargetType.entries.toList(), type, {
            type = it
            if (it == TargetType.SECONDS && value == "8") value = "30"
            if (it == TargetType.REPS && value == "30") value = "8"
        }, Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            when (it) { TargetType.REPS -> "Reps"; TargetType.MAX -> "Max"; TargetType.SECONDS -> "Seconds" }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LhStepper(sets, { sets = it }, 1.0, Modifier.weight(1f), label = "Sets", min = 1.0, max = 20.0)
            if (type != TargetType.MAX) {
                LhStepper(value, { value = it }, if (type == TargetType.SECONDS) 5.0 else 1.0, Modifier.weight(1f),
                    label = if (type == TargetType.SECONDS) "Seconds" else "Reps", min = 1.0)
            }
        }
        LhStepper(weight, { weight = it }, 0.5, Modifier.fillMaxWidth().padding(top = 12.dp), label = "Weight (optional)", suffix = "kg", decimals = 1, placeholder = "–")
        LhButton(
            "Add exercise",
            { onAdd(count, type, if (type == TargetType.MAX) null else v, weight.toDoubleOrNull()?.takeIf { it > 0 }) },
            Modifier.fillMaxWidth().padding(vertical = 16.dp), size = LhButtonSize.Lg, enabled = valid,
        )
    }
}
