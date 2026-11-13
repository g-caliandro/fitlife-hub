package com.lifehub.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifehub.app.core.designsystem.LhRadius
import com.lifehub.app.core.designsystem.LhTheme
import com.lifehub.app.core.designsystem.LhType

/** Square-ish home/quick-action tile: icon above label. Use in a Row with Modifier.weight(1f). */
@Composable
fun LhActionTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LhTheme.colors
    Column(
        modifier.defaultMinSize(minHeight = 88.dp)
            .background(c.card, LhRadius.cardShape)
            .border(1.dp, c.border, LhRadius.cardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = c.primary)
        Spacer(Modifier.height(8.dp))
        Text(label, style = LhType.label, color = c.foreground, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Compact pill "See all ›" replacing text-only links. */
@Composable
fun LhSeeAllChip(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "See all") {
    val c = LhTheme.colors
    Row(
        modifier.height(48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.height(32.dp).background(c.muted, LhRadius.pill).padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = LhType.labelSmall, color = c.foreground)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = c.mutedForeground)
        }
    }
}

/** Top-bar icon action (pencil, calendar-today...). 48dp touch target. */
@Composable
fun LhIconAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, Modifier.size(24.dp), tint = LhTheme.colors.foreground) }
}

/**
 * Bottom content padding for scroll containers of tab screens. AppScaffold pads tab content by bar height (64 + nav
 * inset) + max(28dp cradled-FAB overhang, 48dp active-workout strip), so with this 16dp the last row always ends 16dp
 * above the raised (+) FAB (or the strip) and no row is ever hidden behind it (total = bar + overhang + 16dp).
 */
val LocalTabBottomPadding = androidx.compose.runtime.staticCompositionLocalOf { 16.dp }

/** Height of the docked bottom bar of the current screen; AppScaffold lifts its snackbar host above it. */
val LocalDockedBarHeight = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.MutableState<androidx.compose.ui.unit.Dp>?> { null }

/** Generic docked bottom bar: divider + content, above IME and nav bar. */
@Composable
fun LhBottomActionBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LhTheme.colors
    val docked = LocalDockedBarHeight.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    if (docked != null) androidx.compose.runtime.DisposableEffect(docked) { onDispose { docked.value = 0.dp } }
    Column(
        modifier.fillMaxWidth()
            .onSizeChanged { if (docked != null) docked.value = with(density) { it.height.toDp() } }
            .background(c.background).imePadding().navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

/** Full-width filled Save button docked at the bottom; optional [secondary] (e.g. tonal Delete) above it. */
@Composable
fun LhSaveBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Save",
    enabled: Boolean = true,
    secondary: (@Composable () -> Unit)? = null,
) {
    LhBottomActionBar(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LhButton(label, onClick, Modifier.fillMaxWidth(), size = LhButtonSize.Lg, enabled = enabled)
            secondary?.invoke()
        }
    }
}
