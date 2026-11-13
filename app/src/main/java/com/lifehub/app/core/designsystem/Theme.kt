package com.lifehub.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Immutable
data class LhColors(
    val background: Color, val foreground: Color, val card: Color,
    val muted: Color, val mutedForeground: Color,
    val border: Color, val input: Color, val ring: Color,
    val primary: Color, val primaryForeground: Color,
    val secondary: Color, val secondaryForeground: Color,
    val accent: Color, val accentForeground: Color, val accentMuted: Color,
    val success: Color, val successMuted: Color,
    val warning: Color,
    val destructive: Color, val destructiveForeground: Color,
    val pull: Color, val push: Color, val legs: Color, val core: Color,
    val isDark: Boolean,
    val primaryContainer: Color, val onPrimaryContainer: Color,
    // Macro hues are kept far apart: protein teal, carbs orange (~23°), fat golden yellow (~45°). They color graphics
    // (bars, rings, icons); labels and values next to them stay in `foreground`.
    val track: Color, val kcal: Color, val protein: Color, val carbs: Color, val fat: Color,
    val scrim: Color,
)

val LightLhColors = LhColors(
    background = Color(0xFFF6F4EF), foreground = Color(0xFF24221E), card = Color(0xFFFFFFFF),
    muted = Color(0xFFEFECE5), mutedForeground = Color(0xFF6F6A61),
    border = Color(0xFFECE8DF), input = Color(0xFFE3DED3), ring = Color(0xFF43A047),
    primary = Color(0xFF43A047), primaryForeground = Color(0xFFFFFFFF),
    secondary = Color(0xFFEFECE5), secondaryForeground = Color(0xFF24221E),
    accent = Color(0xFF43A047), accentForeground = Color(0xFFFFFFFF), accentMuted = Color(0xFFE6F2E3),
    success = Color(0xFF2E8B3E), successMuted = Color(0xFFE6F2E3),
    warning = Color(0xFFC77C0E),
    destructive = Color(0xFFD14343), destructiveForeground = Color(0xFFFFFFFF),
    pull = Color(0xFF3F7CC8), push = Color(0xFFE07A2A), legs = Color(0xFF7D8F22), core = Color(0xFF8A63D2),
    isDark = false,
    primaryContainer = Color(0xFFE6F2E3), onPrimaryContainer = Color(0xFF2E7D32),
    track = Color(0xFFEEEAE2), kcal = Color(0xFF43A047), protein = Color(0xFF1F8A80),
    carbs = Color(0xFFD9661F), fat = Color(0xFFDBA200),
    scrim = Color(0x52000000),
)

val DarkLhColors = LhColors(
    background = Color(0xFF171614), foreground = Color(0xFFF2EFE8), card = Color(0xFF222019),
    muted = Color(0xFF2A2721), mutedForeground = Color(0xFFA39E93),
    border = Color(0xFF2E2A23), input = Color(0xFF3A352D), ring = Color(0xFF66BB6A),
    primary = Color(0xFF66BB6A), primaryForeground = Color(0xFF0E1A0F),
    secondary = Color(0xFF2A2721), secondaryForeground = Color(0xFFF2EFE8),
    accent = Color(0xFF66BB6A), accentForeground = Color(0xFF0E1A0F), accentMuted = Color(0xFF26381F),
    success = Color(0xFF6CC070), successMuted = Color(0xFF26381F),
    warning = Color(0xFFE8A23A),
    destructive = Color(0xFFEF7A6E), destructiveForeground = Color(0xFF1A0E0C),
    pull = Color(0xFF74A7E6), push = Color(0xFFF2A15E), legs = Color(0xFFB4C658), core = Color(0xFFB39AF0),
    isDark = true,
    primaryContainer = Color(0xFF26381F), onPrimaryContainer = Color(0xFFA5D6A7),
    track = Color(0xFF332F28), kcal = Color(0xFF66BB6A), protein = Color(0xFF4DB6AC),
    carbs = Color(0xFFF28C4B), fat = Color(0xFFF2C230),
    scrim = Color(0x8F000000),
)

/** Generic tinted-square background for an icon color. */
fun LhColors.tint(color: Color): Color = color.copy(alpha = if (isDark) 0.20f else 0.12f)

val LocalLhColors = staticCompositionLocalOf { LightLhColors }

object LhTheme {
    val colors: LhColors
        @Composable @ReadOnlyComposable get() = LocalLhColors.current
}

private fun LhColors.toM3(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = primaryForeground,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = secondaryForeground,
        secondaryContainer = muted, onSecondaryContainer = foreground,
        tertiary = accent, onTertiary = accentForeground,
        tertiaryContainer = accentMuted, onTertiaryContainer = accent,
        background = background, onBackground = foreground,
        surface = card, onSurface = foreground,
        surfaceVariant = muted, onSurfaceVariant = mutedForeground,
        surfaceTint = Color.Transparent,
        surfaceBright = card, surfaceDim = background,
        surfaceContainerLowest = card, surfaceContainerLow = card,
        surfaceContainer = card, surfaceContainerHigh = card, surfaceContainerHighest = muted,
        outline = input, outlineVariant = border,
        error = destructive, onError = destructiveForeground,
        errorContainer = muted, onErrorContainer = destructive,
        inverseSurface = foreground, inverseOnSurface = background,
        // Snackbar action sits on the inverse surface: use the other theme's green so "Undo" keeps >= 4.5:1 contrast.
        inversePrimary = if (background.luminance() < 0.5f) Color(0xFF2E7D32) else Color(0xFF81C784),
        scrim = Color.Black,
    )
}

private val LhM3Typography = Typography(
    titleLarge = LhType.title,
    titleMedium = LhType.heading,
    bodyMedium = LhType.body,
    bodyLarge = LhType.bodyLarge,
    bodySmall = LhType.caption,
    labelLarge = LhType.label,
    labelMedium = LhType.labelSmall,
    headlineSmall = LhType.stat,
    displaySmall = LhType.timer,
)

private val LhM3Shapes = Shapes(
    extraSmall = LhRadius.smShape, small = LhRadius.smShape,
    medium = LhRadius.mdShape, large = LhRadius.lgShape, extraLarge = LhRadius.sheetShape,
)

@Composable
fun LhTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkLhColors else LightLhColors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LhMotion.reduced = runCatching {
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    CompositionLocalProvider(LocalLhColors provides colors) {
        MaterialTheme(colorScheme = colors.toM3(), typography = LhM3Typography, shapes = LhM3Shapes) {
            androidx.compose.material3.Surface(color = colors.background, contentColor = colors.foreground, content = content)
        }
    }
}
