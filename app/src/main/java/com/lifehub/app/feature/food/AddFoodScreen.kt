package com.lifehub.app.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.SolidColor
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.components.LhAddRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.lifehub.app.R
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifehub.app.container
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType
import com.lifehub.app.core.designsystem.components.LhButton
import com.lifehub.app.core.designsystem.components.LhButtonVariant
import com.lifehub.app.core.designsystem.components.LhEmptyState
import com.lifehub.app.core.designsystem.components.LhIconAction
import com.lifehub.app.core.designsystem.components.LhSegmentedControl
import com.lifehub.app.core.designsystem.components.LhTextField
import com.lifehub.app.core.designsystem.components.LhTopBar
import com.lifehub.app.core.designsystem.fmtDate
import com.lifehub.app.data.model.Food
import com.lifehub.app.navigation.Routes
import java.time.LocalDate

@Composable
fun AddFoodRoute(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContext.current.container
    val vm: AddFoodViewModel = viewModel(factory = AddFoodViewModel.factory(c))
    val state by vm.state.collectAsState()
    AddFoodScreen(
        state, onBack,
        onTab = vm::setTab, onQuery = vm::setQuery,
        onPick = { f -> vm.pick(f) { stored -> onNavigate(Routes.foodDetail(stored.id, state.date)) } },
        onScan = { onNavigate(Routes.foodScan(state.date)) },
        onCreateCustom = { onNavigate(Routes.foodCustom()) },
        onQuickAdd = vm::quickAdd,
    )
}

@Composable
fun AddFoodScreen(
    state: AddFoodState, onBack: () -> Unit,
    onTab: (FoodTab) -> Unit, onQuery: (String) -> Unit, onPick: (Food) -> Unit,
    onScan: () -> Unit, onCreateCustom: () -> Unit,
    onQuickAdd: ((Food) -> Unit)? = null,
) {
    val c = LhTheme.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        LhTopBar("Add food", subtitle = fmtDate(state.date, state.today.year), onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchPill(
                state.query, onQuery, if (state.tab == FoodTab.SEARCH) "Search foods…" else "Filter your foods",
                onSearch = { onTab(FoodTab.SEARCH) }, onScan = onScan,
            )
            LhSegmentedControl(FoodTab.entries, state.tab, onTab, Modifier.fillMaxWidth().testTag("food-tabs")) { it.label }
        }
        when (state.tab) {
            FoodTab.RECENT -> FoodList(
                state.recent, onPick, onQuickAdd, "No recent foods", "Foods you log show up here.",
                footer = null, onCreateCustom = onCreateCustom, filtered = state.query.isNotBlank(),
            )
            FoodTab.MINE -> FoodList(
                state.mine, onPick, onQuickAdd, "No custom foods yet", "Create your own foods or star products to keep them here.",
                footer = { LhAddRow(onCreateCustom, Modifier.testTag("food-create"), label = "Create food") },
                onCreateCustom = onCreateCustom, filtered = state.query.isNotBlank(),
            )
            FoodTab.SEARCH -> when (val s = state.search) {
                SearchState.Idle -> Text("Type at least 2 letters, or scan a barcode.", style = LhType.captionMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                SearchState.Loading -> Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { Box(Modifier.fillMaxWidth().height(72.dp).background(c.muted, LhRadius.cardShape)) }
                }
                is SearchState.Error -> LhEmptyState("Can't reach Open Food Facts", description = "Recent and My foods work offline.", icon = Icons.Outlined.CloudOff,
                    actionText = "Retry", onAction = { onQuery(state.query) })
                is SearchState.Results ->
                    if (s.foods.isEmpty()) LhEmptyState("No results", description = "Try another name, or create the food yourself.", icon = Icons.Outlined.Search,
                        actionText = "Create custom food", onAction = onCreateCustom, actionIcon = Icons.Outlined.Add)
                    else FoodList(s.foods, onPick, onQuickAdd, "", "", footer = null, onCreateCustom = onCreateCustom, filtered = false)
            }
        }
    }
}

@Composable
private fun SearchPill(value: String, onValue: (String) -> Unit, placeholder: String, onSearch: () -> Unit, onScan: () -> Unit) {
    val c = LhTheme.colors
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(c.card, LhRadius.pill).border(1.dp, c.input, LhRadius.pill).padding(start = 16.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.mutedForeground, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = LhType.bodyLarge, color = c.mutedForeground, maxLines = 1)
            BasicTextField(
                value, onValue, Modifier.fillMaxWidth().testTag("food-search"), singleLine = true,
                textStyle = LhType.bodyLarge.copy(color = c.foreground), cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
        }
        LhIconAction(ImageVector.vectorResource(R.drawable.ic_barcode), "Scan barcode", onScan, Modifier.testTag("food-scan"))
    }
}

@Composable
private fun FoodList(
    foods: List<Food>, onPick: (Food) -> Unit, onQuickAdd: ((Food) -> Unit)?, emptyTitle: String, emptyBody: String,
    footer: (@Composable () -> Unit)?, onCreateCustom: () -> Unit, filtered: Boolean,
) {
    if (foods.isEmpty() && footer == null) {
        LhEmptyState(if (filtered) "No match" else emptyTitle, description = if (filtered) "Nothing in this list matches. Use the Search tab for Open Food Facts." else emptyBody, icon = Icons.Outlined.Restaurant)
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("food-list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(foods, key = { it.id }) { f -> FoodRow(f, { onPick(f) }, Modifier.testTag("food-row-${f.id}"), onQuickAdd?.let { q -> { q(f) } }) }
        if (foods.isEmpty()) item("empty") {
            Text(if (filtered) "Nothing matches." else emptyBody, style = LhType.captionMuted, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (footer != null) item("footer") { footer() }
    }
}
