package com.lifehub.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifehub.app.data.model.DayType
import com.lifehub.app.data.model.Exercise

/** Exercise artwork lookup: drawable `ex_<slug>`; see docs/exercise-icons.md. */
object ExerciseIcons {
    /** "Pull-up" -> "pull_up", "L-sit" -> "l_sit". */
    fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    fun resourceName(name: String): String = "ex_" + slug(name)
}

/** Big exercise illustration, or a tinted circle with the day-type icon (or a dumbbell) when no asset exists yet. */
@Composable
fun ExerciseIcon(name: String, size: Dp, modifier: Modifier = Modifier, dayType: DayType? = null) {
    val context = LocalContext.current
    val c = LhTheme.colors
    val resId = remember(name) { context.resources.getIdentifier(ExerciseIcons.resourceName(name), "drawable", context.packageName) }
    if (resId != 0) {
        Icon(painterResource(resId), null, modifier.size(size), tint = c.foreground)
    } else {
        val tint: Color = dayType?.let { c.dayType(it) } ?: c.accent
        Box(modifier.size(size).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            if (dayType != null) DayTypeIcon(dayType, size = size * 0.5f, tint = tint)
            else Icon(Icons.Outlined.FitnessCenter, null, Modifier.size(size * 0.5f), tint = tint)
        }
    }
}

@Composable
fun ExerciseIcon(exercise: Exercise, size: Dp, modifier: Modifier = Modifier, dayType: DayType? = null) =
    ExerciseIcon(exercise.name, size, modifier, dayType)
