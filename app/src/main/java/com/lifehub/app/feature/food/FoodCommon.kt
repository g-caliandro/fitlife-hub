package com.lifehub.app.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import com.lifehub.app.core.designsystem.LhRadius
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhCard
import com.lifehub.app.core.designsystem.components.LhListItem
import com.lifehub.app.core.designsystem.components.LhLetterSquare
import com.lifehub.app.core.designsystem.fmtInt
import com.lifehub.app.data.model.Food
import com.lifehub.app.data.model.FoodEntry
import kotlin.math.roundToInt
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage

/** "165 kcal · P 31 · C 0 · F 4" per 100 g. */
fun per100Line(f: Food): String =
    "${fmtInt(f.kcal100.roundToInt())} kcal · P ${fmtMacro(f.protein100)} · C ${fmtMacro(f.carbs100)} · F ${fmtMacro(f.fat100)}"

/** Product photo (Open Food Facts) over the initial square, which stays visible while loading, on error or without a URL. */
@Composable
fun FoodThumb(name: String, imageUrl: String?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(modifier.size(size)) {
        LhLetterSquare(name, size = size)
        if (imageUrl != null) AsyncImage(
            imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(LhRadius.itemShape),
        )
    }
}

/** Result / recent / my-foods row: photo or initial square, name, "brand · per 100 g", kcal trailing. */
@Composable
fun FoodRow(food: Food, onClick: () -> Unit, modifier: Modifier = Modifier, onQuickAdd: (() -> Unit)? = null) {
    val kcal = "${fmtInt(food.kcal100.roundToInt())} kcal"
    val brand = food.brand?.takeIf { it.isNotBlank() }
    // With the quick-add "+" the kcal moves into the subtitle so the food name keeps the width (no "Chicken brea…").
    val subtitle = if (onQuickAdd != null) listOfNotNull("$kcal / 100 g", brand).joinToString(" · ")
    else listOfNotNull(brand, "per 100 g").joinToString(" · ")
    LhListItem(
        food.name, modifier, subtitle = subtitle,
        leading = { FoodThumb(food.name, food.imageUrl) }, trailingText = kcal, onClick = onClick,
        trailing = onQuickAdd?.let { add -> {
            val c = LhTheme.colors
            // Quick add (spec §3.3): 40dp tonal "+" inside a 48dp touch target.
            Box(
                Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Quick add", onClick = add)
                    .testTag("food-quick-add-${food.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(40.dp).background(c.primaryContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Add, "Quick add ${food.name}", tint = c.onPrimaryContainer, modifier = Modifier.size(22.dp))
                }
            }
        } },
    )
}

/** Logged entry: photo or initial square, name, "150 g · P 46 · C 8 · F 6", trailing kcal. Used in the diary day. */
@Composable
fun FoodEntryRow(entry: FoodEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LhListItem(
        entry.name, modifier, subtitle = "${fmtGrams(entry.grams)} g · P ${fmtMacro(entry.proteinG)} · C ${fmtMacro(entry.carbsG)} · F ${fmtMacro(entry.fatG)}",
        leading = { FoodThumb(entry.name, entry.imageUrl) }, trailingText = "${fmtInt(entry.kcal.roundToInt())} kcal", onClick = onClick,
    )
}

/** A quick add in the Diary food list: its name (or "Quick add"), macros with "n/a" for unknown ones, kcal. */
@Composable
fun QuickAddRow(entry: com.lifehub.app.data.model.QuickAddEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LhListItem(
        entry.displayName, modifier, subtitle = com.lifehub.app.feature.nutrition.macroSummary(entry.proteinG, entry.carbsG, entry.fatG),
        icon = Icons.Outlined.Bolt, iconTint = LhTheme.colors.protein,
        trailingText = "${fmtInt(entry.kcal)} kcal", onClick = onClick,
    )
}

@Composable
fun MacroFigures(kcal: Double, protein: Double, carbs: Double, fat: Double, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        @Composable fun fig(label: String, value: String, unit: String) =
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = LhType.tnum(LhType.title), color = c.foreground, modifier = Modifier.padding(bottom = 2.dp))
                Text("$label · $unit", style = LhType.captionMuted)
            }
        fig("Calories", fmtInt(kcal.roundToInt()), "kcal")
        fig("Protein", fmtMacro(protein), "g")
        fig("Carbs", fmtMacro(carbs), "g")
        fig("Fat", fmtMacro(fat), "g")
    }
}

/** Swipe left to delete; the row stays until the data flow removes it (caller shows an Undo snackbar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteEntry(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val c = LhTheme.colors
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state, enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(Modifier.fillMaxSize().background(c.destructive, LhRadius.cardShape).padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Outlined.Delete, "Delete", tint = c.destructiveForeground)
                }
            }
        },
    ) { content() }
}
