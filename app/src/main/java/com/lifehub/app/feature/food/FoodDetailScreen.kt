package com.lifehub.app.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import com.lifehub.app.core.designsystem.fmtInt
import com.lifehub.app.core.designsystem.components.LhProgressBar
import com.lifehub.app.core.designsystem.MetricIcon
import com.lifehub.app.core.designsystem.MetricKind
import androidx.compose.foundation.layout.width
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.SnackbarBus
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhChip
import com.lifehub.app.core.designsystem.components.LhConfirmDialog
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhIconAction
import com.lifehub.app.core.designsystem.components.LhSaveBar
import com.lifehub.app.core.designsystem.components.LhStepper
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.fmtDate
import com.lifehub.app.data.model.FoodSource
import com.lifehub.app.domain.macrosFor
import com.lifehub.app.navigation.Routes

@Composable
fun FoodDetailRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: FoodDetailViewModel = viewModel(factory = FoodDetailViewModel.factory(c))
    val state by vm.state.collectAsState()
    FoodDetailScreen(
        state, onBack, vm::setGrams, vm::toggleFavorite,
        onEditFood = { state.food?.let { onNavigate(Routes.foodCustom(it.id)) } },
        onSave = {
            vm.save { msg, undo ->
                // Pop detail (+ the add-food list when adding) and confirm on the screen we land on.
                onBack(); if (!state.editing) onBack()
                SnackbarBus.show(msg, "Undo", undo)
            }
        },
        onDelete = { vm.delete { msg, undo -> onBack(); SnackbarBus.show(msg, "Undo", undo) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodDetailScreen(
    state: FoodDetailState, onBack: () -> Unit, onGrams: (String) -> Unit, onFavorite: () -> Unit,
    onEditFood: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit,
) {
    val c = LhTheme.colors
    val food = state.food
    var confirm by remember { mutableStateOf(false) }
    val grams = parseGrams(state.grams)
    Column(Modifier.fillMaxSize().background(c.background)) {
        LhTopBar(food?.name ?: "Food", subtitle = food?.brand, onBack = onBack, actions = {
            if (food != null && !food.id.startsWith("snapshot-")) {
                if (food.source == FoodSource.CUSTOM) LhIconAction(Icons.Outlined.Edit, "Edit food", onEditFood)
                LhIconAction(if (food.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (food.favorite) "Remove favorite" else "Favorite", onFavorite, Modifier.testTag("food-favorite"))
            }
        })
        if (food == null) {
            if (!state.loading) LhEmptyState("Food not found", description = "It may have been deleted.")
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val t = food.macrosFor(grams ?: 0.0)
            LhCard(Modifier.fillMaxWidth().testTag("food-macros")) {
                val kt = state.kcalTarget?.takeIf { it > 0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoodThumb(food.name, food.imageUrl, size = 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(fmtInt(t.kcalInt), style = LhType.tnum(LhType.title), color = c.foreground)
                            Text(" kcal", style = LhType.captionMuted, modifier = Modifier.padding(bottom = 2.dp))
                        }
                        if (kt != null) Text("${(t.kcal / kt * 100).roundToInt()}% of your ${fmtInt(kt)} kcal target", style = LhType.captionMuted)
                    }
                }
                if (kt != null) {
                    Spacer(Modifier.height(12.dp))
                    LhProgressBar((t.kcal / kt).toFloat(), color = c.kcal)
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MacroStat(MetricKind.Carbs, t.carbsG)
                    MacroStat(MetricKind.Fat, t.fatG)
                    MacroStat(MetricKind.Protein, t.proteinG)
                }
                Text(per100Line(food) + " per 100 g", style = LhType.tnum(LhType.captionMuted), modifier = Modifier.padding(top = 12.dp))
            }
            LhStepper(
                state.grams, onGrams, step = 10.0, label = "Amount", suffix = "g", decimals = 1, min = 0.0, max = 10_000.0,
                modifier = Modifier.fillMaxWidth().testTag("food-grams"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                gramChips(food.servingSizeG).forEach { chip ->
                    LhChip(chip.label, selected = grams == chip.grams, onClick = { onGrams(fmtGrams(chip.grams)) }, modifier = Modifier.testTag("chip-${chip.grams.toInt()}"))
                }
            }
        }
        val dateLabel = fmtDate(state.date, state.today.year)
        LhSaveBar(
            onSave, label = if (state.editing) "Save changes" else if (state.date == state.today) "Add to today" else "Add to $dateLabel",
            enabled = grams != null,
            secondary = if (state.editing) ({
                LhButton("Delete", { confirm = true }, Modifier.fillMaxWidth(), variant = LhButtonVariant.Outline, leadingIcon = Icons.Outlined.Delete)
            }) else null,
        )
    }
    if (confirm) LhConfirmDialog(
        title = "Delete this entry?", message = "It will be removed from ${fmtDate(state.date, state.today.year)}.",
        confirmText = "Delete", onConfirm = { confirm = false; onDelete() }, onDismiss = { confirm = false },
    )
}

/** "12 g" over "carbs" with the macro icon. */
@Composable
private fun MacroStat(metric: MetricKind, grams: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MetricIcon(metric, 20.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text("${fmtMacro(grams)} g", style = LhType.tnum(LhType.label), color = LhTheme.colors.foreground, maxLines = 1)
            Text(metric.label.lowercase(), style = LhType.caption, color = LhTheme.colors.mutedForeground, maxLines = 1)
        }
    }
}
