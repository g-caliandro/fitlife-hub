package com.lifehub.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.core.ui.GoalProgressCard
import com.lifehub.app.core.ui.GoalCardModel
import com.lifehub.app.core.ui.WeekdayChips
import com.lifehub.app.data.model.GoalState
import com.lifehub.app.domain.PlanProgress
import com.lifehub.app.domain.timeProgress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@Composable
fun DesignCatalog() {
    var seg by remember { mutableStateOf("1W") }
    var chip by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LhTopBar("Goals", onBack = {}, actions = { LhIconButton(Icons.Outlined.Add, "Add", {}) })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LhButton("Primary", {})
                LhButton("Secondary", {}, variant = LhButtonVariant.Secondary)
                LhButton("Outline", {}, variant = LhButtonVariant.Outline)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LhButton("Ghost", {}, variant = LhButtonVariant.Ghost, leadingIcon = Icons.Outlined.Settings)
                LhButton("Delete", {}, variant = LhButtonVariant.Destructive, size = LhButtonSize.Sm)
                LhButton("Disabled", {}, enabled = false)
            }
            LhButton("Start workout", {}, Modifier.fillMaxWidth(), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.Add)
            LhTextField("Muscle Up", {}, label = "Name")
            LhTextField("", {}, label = "Notes", placeholder = "Optional", error = "Required")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhNumberField("76.9", {}, Modifier.weight(1f), label = "Weight", suffix = "kg", decimals = 1)
                LhStepper("3", {}, 1.0, Modifier.weight(1f), label = "Sets")
            }
            LhDatePickerField(LocalDate.of(2026, 11, 12), {}, label = "Start date")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LhBadge("Default", variant = LhBadgeVariant.Default)
                LhBadge("Secondary")
                LhBadge("Outline", variant = LhBadgeVariant.Outline)
                LhBadge("Live", variant = LhBadgeVariant.Accent)
                LhBadge("Done", variant = LhBadgeVariant.Success)
            }
            LhProgressBar(0.6f)
            LhSegmentedControl(listOf("1W", "2W"), seg, { seg = it }) { it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LhChip("All", chip, { chip = !chip }); LhChip("Pull", !chip, { chip = !chip })
            }
            WeekdayChips(setOf(1, 3, 5), null, highlightToday = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhStatTile("Weight", "76.9", Modifier.weight(1f), unit = "kg", delta = -0.3, deltaText = "−0.3 kg", good = DeltaGood.Neutral)
                LhStatTile("Muscle", "37.3", Modifier.weight(1f), unit = "kg", delta = 0.1, deltaText = "+0.1 kg", caption = "best week")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhStatTile("Fat", "12.5", Modifier.weight(1f), unit = "kg", delta = 0.2, deltaText = "+0.2 kg", good = DeltaGood.Down)
                LhStatTile("Sessions", "—", Modifier.weight(1f), delta = null)
            }
            GoalProgressCard(
                GoalCardModel("g", "Muscle Up", GoalState.ACTIVE, timeProgress(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 12)),
                    listOf(PlanProgress("p", "Pull & Bar", 26, 36), PlanProgress("q", "Core", 5, null))), {},
            )
            LhCard { LhListRow("Reminders", subtitle = "Daily at 18:00", onClick = {}); LhSwitchRow("Train", true, {}, showDivider = false) }
            LhEmptyState("No active goals", description = "Set a time window for a skill you're chasing.", actionText = "Add goal", onAction = {})
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1600dp-xxhdpi")
class DesignSystemScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun catalogLight() {
        rule.setContent { LhTheme(darkTheme = false) { DesignCatalog() } }
        rule.onRoot().captureRoboImage("build/screens/designsystem-light.png")
    }

    @Test fun catalogDark() {
        rule.setContent { LhTheme(darkTheme = true) { DesignCatalog() } }
        rule.onRoot().captureRoboImage("build/screens/designsystem-dark.png")
    }
}
