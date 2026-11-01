package com.example.minesweeper.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.minesweeper.model.AppColorTheme

// Official Google Material 3 Shape Scale
val M3Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

fun createM3ColorScheme(theme: AppColorTheme) = darkColorScheme(
    primary = theme.primary,
    onPrimary = theme.onPrimary,
    primaryContainer = theme.primarySubtle,
    onPrimaryContainer = theme.textPrimary,
    secondary = theme.primaryLight,
    onSecondary = theme.onPrimary,
    secondaryContainer = theme.surfaceVariant,
    onSecondaryContainer = theme.textPrimary,
    tertiary = theme.primarySubtle,
    onTertiary = theme.textPrimary,
    background = BlackPure,
    onBackground = theme.textPrimary,
    surface = theme.surface,
    onSurface = theme.textPrimary,
    surfaceVariant = theme.surfaceVariant,
    onSurfaceVariant = theme.textMuted,
    surfaceContainer = theme.surface,
    surfaceContainerHigh = theme.surfaceVariant,
    surfaceContainerHighest = theme.surfaceTile,
    outline = theme.outline,
    outlineVariant = theme.outline
)

@Composable
fun MinesweeperTheme(
    colorTheme: AppColorTheme = AppColorTheme.OLIVE_GOLD,
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = createM3ColorScheme(colorTheme),
        typography = Typography,
        shapes = M3Shapes,
        content = content
    )
}