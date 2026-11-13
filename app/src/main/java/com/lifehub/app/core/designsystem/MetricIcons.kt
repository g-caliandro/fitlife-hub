package com.lifehub.app.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.KebabDining
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tracked metrics with an icon. (Not named `Metric`: that is the insights delta type in `domain`.)
 * A drawable `metric_<name>` (e.g. `metric_protein`) overrides the Material fallback when present.
 */
enum class MetricKind(val label: String, val fallback: ImageVector) {
    Calories("Calories", Icons.Outlined.LocalFireDepartment),
    Protein("Protein", Icons.Outlined.KebabDining),
    Fat("Fat", Icons.Outlined.WaterDrop),
    Carbs("Carbs", Icons.Outlined.BakeryDining),
    Training("Training", Icons.Outlined.FitnessCenter);

    val resourceName: String get() = "metric_" + name.lowercase()
}

/** Theme color of a metric (bars, charts, icon tint). */
@Composable
fun MetricKind.color(): Color {
    val c = LhTheme.colors
    return when (this) {
        MetricKind.Calories -> c.kcal
        MetricKind.Protein -> c.protein
        MetricKind.Fat -> c.fat
        MetricKind.Carbs -> c.carbs
        MetricKind.Training -> c.accent
    }
}

/** Metric icon: custom `metric_*` drawable if bundled, else the Material icon. Pass [Color.Unspecified] to keep artwork colors. */
@Composable
fun MetricIcon(metric: MetricKind, size: Dp = 18.dp, modifier: Modifier = Modifier, tint: Color = metric.color(), contentDescription: String? = null) {
    val context = LocalContext.current
    val resId = remember(metric) { context.resources.getIdentifier(metric.resourceName, "drawable", context.packageName) }
    if (resId != 0) Icon(painterResource(resId), contentDescription, modifier.size(size), tint = tint)
    else Icon(metric.fallback, contentDescription, modifier.size(size), tint = tint)
}
