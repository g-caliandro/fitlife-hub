package com.lifehub.app.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import com.lifehub.app.core.designsystem.LhRadius
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.*

/** RPE 1-10 chip grid (tap again to clear). Shared with the session edit screen. */
@Composable
fun RpeChips(rpe: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (rowStart in listOf(1, 6)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in rowStart until rowStart + 5) {
                    LhChip("$n", rpe == n, { onChange(if (rpe == n) null else n) }, Modifier.weight(1f).testTag("rpe-$n"))
                }
            }
        }
    }
}

/** Item 16: summary before saving. With unlogged sets the choice is "Finish anyway" (saved as skipped) / "Back to workout". */
@Composable
fun FinishSheet(
    summary: WorkoutOps.Summary, minutes: Int, loadPrs: suspend () -> List<String>,
    onSave: (notes: String?, rpe: Int?) -> Unit, onDismiss: () -> Unit,
) {
    val c = LhTheme.colors
    var rpe by rememberSaveable { mutableStateOf<Int?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    val prs by produceState(emptyList<String>()) { value = try { loadPrs() } catch (_: Exception) { emptyList() } }
    LhBottomSheet(onDismiss, title = "Workout summary") {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(72.dp).background(c.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, null, Modifier.size(40.dp), tint = c.primaryForeground)
            }
            Text(
                "$minutes min", style = LhType.body, color = c.mutedForeground,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 16.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhStatTile("Sets done", "${summary.done}", Modifier.weight(1f))
                LhStatTile("Skipped", "${summary.skipped + summary.pending}", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LhStatTile("Volume", "${summary.reps}", Modifier.weight(1f), unit = "reps")
                LhStatTile("Holds", "${summary.holdSeconds}", Modifier.weight(1f), unit = "s")
            }
            if (prs.isNotEmpty()) {
                LhSectionHeader("New records", Modifier.padding(top = 20.dp), icon = Icons.Outlined.EmojiEvents)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    prs.forEach { LhListItem(it, icon = Icons.Outlined.EmojiEvents, iconTint = c.warning) }
                }
            }
            if (summary.pending > 0) {
                Text(
                    "${summary.pending} sets not logged yet. They will be saved as skipped.", style = LhType.caption,
                    color = c.warning, modifier = Modifier.padding(top = 12.dp),
                )
            }
            Text("How hard was it? (RPE, optional)", style = LhType.label, color = c.foreground, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            RpeChips(rpe, { rpe = it })
            LhTextField(notes, { notes = it }, Modifier.padding(top = 16.dp), label = "Notes", singleLine = false, minLines = 3)
            LhButton(
                if (summary.pending > 0) "Finish anyway" else "Save workout", { onSave(notes.ifBlank { null }, rpe) },
                Modifier.fillMaxWidth().padding(top = 16.dp), size = LhButtonSize.Lg, leadingIcon = Icons.Outlined.Check,
            )
            LhButton(
                "Back to workout", onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                variant = LhButtonVariant.Tonal, size = LhButtonSize.Lg, leadingIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            )
        }
    }
}

