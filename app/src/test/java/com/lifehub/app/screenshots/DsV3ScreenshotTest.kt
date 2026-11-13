package com.lifehub.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.components.*
import com.lifehub.app.core.ui.charts.BarChart
import com.lifehub.app.core.ui.charts.ChartPoint
import com.lifehub.app.core.ui.charts.DualLineChart
import com.lifehub.app.core.ui.charts.LineSeries
import com.lifehub.app.data.model.DayType
import com.lifehub.app.domain.DateRange
import com.lifehub.app.navigation.AddActionSheet
import com.lifehub.app.navigation.LhBottomBar
import com.lifehub.app.navigation.Tab
import com.lifehub.app.feature.you.YouScreen
import com.lifehub.app.feature.you.YouState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@Composable
private fun Cat1() {
    val c = LhTheme.colors
    Column(Modifier.fillMaxWidth().background(c.background).padding(bottom = 16.dp)) {
        LhScreenHeader("Diary", Icons.Filled.Home, actions = { LhIconAction(Icons.Outlined.Today, "Today", {}) })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LhValueChip("76.7 kg", icon = Icons.Outlined.MonitorWeight, onClick = {})
                LhValueChip("5-day streak", icon = Icons.Outlined.LocalFireDepartment, iconTint = c.push, trailingIcon = null)
            }
            LhCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { LhIconSquare(Icons.Outlined.ArrowDownward, size = 40.dp, iconSize = 20.dp); androidx.compose.material3.Text("1,783", style = com.lifehub.app.core.designsystem.LhType.stat); androidx.compose.material3.Text("eaten", style = com.lifehub.app.core.designsystem.LhType.captionMuted) }
                    Column(horizontalAlignment = Alignment.End) { LhIconSquare(Icons.Outlined.EggAlt, tint = c.protein, size = 40.dp, iconSize = 20.dp); androidx.compose.material3.Text("90 g", style = com.lifehub.app.core.designsystem.LhType.stat); androidx.compose.material3.Text("protein", style = com.lifehub.app.core.designsystem.LhType.captionMuted) }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LhRing(0.7f, "817", "kcal left") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhMacroCard("carbs", c.carbs, 205.0, 250.0, Modifier.weight(1f))
                LhMacroCard("fat", c.fat, 67.0, null, Modifier.weight(1f))
                LhMacroCard("protein", c.protein, 90.0, 150.0, Modifier.weight(1f))
            }
            LhSectionHeader("Training", icon = Icons.Outlined.FitnessCenter, topPadding = 8.dp, action = { LhSeeAllChip({}) })
            LhListItem("Push day", subtitle = "5 exercises · Wed", leading = { LhIconSquare(DayType.PUSH) },
                trailing = { LhButton("Start", {}, variant = LhButtonVariant.Tonal, size = LhButtonSize.Sm, leadingIcon = Icons.Outlined.PlayArrow) })
            LhListItem("Greek yogurt", subtitle = "200 g · P 20 · C 8 · F 6", leading = { LhLetterSquare("G") }, trailingText = "320 kcal")
            LhAddRow({})
            LhKcalChip("2,432/2,600 kcal")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LhMacroRingStat("247/250 g", "carbs", 0.99f, c.carbs); LhMacroRingStat("88/80 g", "fat", 1.1f, c.fat); LhMacroRingStat("163/150 g", "protein", 0.4f, c.protein)
            }
            LhSegmentedControl(listOf("7d", "30d", "90d", "All"), "30d", {}) { it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { LhChip("Mon", true, {}); LhChip("Tue", false, {}); LhChip("Wed", false, {}) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhStatTile("Sessions", "2", Modifier.weight(1f), delta = -2.0, deltaText = "-2")
                LhStatTile("Avg reps", "11.3", Modifier.weight(1f), delta = 1.2, caption = "per set")
            }
            LhGroupCard {
                LhListRow("Goals", subtitle = "2 active", leadingIcon = Icons.Outlined.Flag, trailing = { androidx.compose.material3.Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) })
                LhSwitchRow("Reminders", true, {}, showDivider = false)
            }
            LhProgressBar(0.6f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LhButton("Primary", {}, leadingIcon = Icons.Outlined.PlayArrow)
                LhButton("Outline", {}, variant = LhButtonVariant.Outline)
                LhButton("Delete", {}, variant = LhButtonVariant.Destructive)
            }
            LhNumberField("200", {}, label = "Amount", suffix = "g")
            LhCollapsibleCard("Push · 46 min · RPE 8", expanded = true, onToggle = {}, subtitle = "3/5 exercises",
                leading = { LhIconSquare(DayType.PUSH) }, trailing = { androidx.compose.material3.Text("81%", style = com.lifehub.app.core.designsystem.LhType.stat) }) {
                androidx.compose.material3.Text("Bench press   8 · 8 · 6")
            }
            LhEmptyState("No goals", description = "Set a time window for a skill.", icon = Icons.Outlined.Flag, actionText = "Add goal", onAction = {})
        }
    }
}

@Composable
private fun Cat2() {
    val c = LhTheme.colors
    val today = LocalDate.of(2026, 11, 12)
    val range = DateRange(today.minusDays(6), today)
    val pts = range.days.mapIndexed { i, d -> ChartPoint(d, if (i == 3) 0.0 else 1800.0 + i * 130) }
    Column(Modifier.fillMaxWidth().background(c.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LhCard { BarChart(range, pts.map { ChartPoint(it.date, it.value / 20) }, barColor = c.protein, targetLine = 100.0, today = today) }
        LhCard { DualLineChart(range, LineSeries("Weight", range.days.mapIndexed { i, d -> ChartPoint(d, 77.0 - i * 0.2) }, c.primary, "kg"), LineSeries("Fat", range.days.mapIndexed { i, d -> ChartPoint(d, 14.0 - i * 0.1) }, c.fat, "%"), today = today) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LhRing(0.4f, "0:14", "hold", size = 200.dp, stroke = 12.dp) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LhRing(1.2f, "2,900", "kcal eaten") }
    }
}

@Composable
private fun Cat3() {
    Column(Modifier.fillMaxWidth().background(LhTheme.colors.background)) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp)) { LhBottomBar(Tab.Home.route, onTab = {}) }
        Box(Modifier.fillMaxWidth().padding(top = 40.dp)) { LhBottomBar(Tab.Trends.route, onTab = {}, addOpen = true) }
        YouScreen(YouState(76.7, 14.2, 2, 2, 12, true, "Protein 150 g · 2,600 kcal"), {}, LocalDate.of(2026, 11, 12))
    }
}

abstract class DsV3Base(private val dark: Boolean) {
    @get:Rule val rule = createComposeRule()
    private val n get() = if (dark) "dark" else "light"
    @Test fun components() { rule.setContent { LhTheme(dark) { Cat1() } }; rule.onRoot().captureRoboImage("build/screens/ds-v3-$n-components.png") }
    @Test fun charts() { rule.setContent { LhTheme(dark) { Cat2() } }; rule.onRoot().captureRoboImage("build/screens/ds-v3-$n-charts.png") }
    @Test fun shell() { rule.setContent { LhTheme(dark) { Cat3() } }; rule.onRoot().captureRoboImage("build/screens/ds-v3-$n-shell.png") }
    @Test fun sheet() { rule.setContent { LhTheme(dark) { AddActionSheet({}, "Push · 23 min", {}, {}, {}, {}) } }; rule.waitForIdle(); rule.onAllNodes(androidx.compose.ui.test.isRoot()).onLast().captureRoboImage("build/screens/ds-v3-$n-sheet.png") }
}

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w360dp-h1800dp-xxhdpi")
class DsV3LightTest : DsV3Base(false)

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w360dp-h1800dp-xxhdpi")
class DsV3DarkTest : DsV3Base(true)
