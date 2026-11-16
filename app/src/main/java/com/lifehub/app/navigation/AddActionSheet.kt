package com.lifehub.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.components.LhBottomSheet
import com.lifehub.app.core.designsystem.components.LhListItem

@Composable
fun AddActionSheet(
    onDismiss: () -> Unit,
    activeWorkout: String?,
    onAddFood: () -> Unit,
    onQuickMacros: () -> Unit,
    onStartWorkout: () -> Unit,
    onLogWeight: () -> Unit,
    onAskCopilot: () -> Unit = {},
) {
    val c = LhTheme.colors
    LhBottomSheet(onDismiss, title = "Add") {
        Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LhListItem("Add food", icon = Icons.Outlined.Restaurant, iconTint = c.primary, subtitle = "Search, scan or pick a recent food", onClick = onAddFood, modifier = Modifier.padding(0.dp))
            LhListItem("Quick macros", icon = Icons.Outlined.Bolt, iconTint = c.protein, subtitle = "Calories, macros optional", onClick = onQuickMacros)
            LhListItem(
                if (activeWorkout != null) "Resume workout" else "Start workout", icon = Icons.Outlined.FitnessCenter, iconTint = c.push,
                subtitle = activeWorkout ?: "Pick a plan", onClick = onStartWorkout,
            )
            LhListItem("Log weight", icon = Icons.Outlined.MonitorWeight, iconTint = c.carbs, subtitle = "Weight and body fat", onClick = onLogWeight)
            LhListItem("Ask copilot", icon = Icons.Outlined.AutoAwesome, iconTint = c.primary, subtitle = "Questions about your food, training and goals", onClick = onAskCopilot)
        }
    }
}
