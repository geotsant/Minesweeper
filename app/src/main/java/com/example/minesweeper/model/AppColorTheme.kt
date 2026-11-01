package com.example.minesweeper.model

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class AppColorTheme(
    val id: String,
    val displayName: String,
    val primary: Color,
    val primaryLight: Color,
    val primarySubtle: Color,
    val onPrimary: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceTile: Color,
    val outline: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textDark: Color
) {
    OLIVE_GOLD(
        id = "olive_gold",
        displayName = "Olive Gold",
        primary = Color(0xFF85804C),
        primaryLight = Color(0xFFA6A168),
        primarySubtle = Color(0xFF424026),
        onPrimary = Color(0xFF000000),
        surface = Color(0xFF0E0F0C),
        surfaceVariant = Color(0xFF171813),
        surfaceTile = Color(0xFF2B2D25),
        outline = Color(0xFF26281E),
        textPrimary = Color(0xFFE2DC96),
        textMuted = Color(0xFFA8A36E),
        textDark = Color(0xFF6B6743)
    ),
    TERRACOTTA_RED(
        id = "terracotta_red",
        displayName = "Terracotta Red",
        primary = Color(0xFF8C473A),
        primaryLight = Color(0xFFB06557),
        primarySubtle = Color(0xFF46231D),
        onPrimary = Color(0xFF000000),
        surface = Color(0xFF110D0C),
        surfaceVariant = Color(0xFF1A1312),
        surfaceTile = Color(0xFF2D2524),
        outline = Color(0xFF2A1E1D),
        textPrimary = Color(0xFFE89F96),
        textMuted = Color(0xFFB3756C),
        textDark = Color(0xFF75433C)
    ),
    SLATE_BLUE(
        id = "slate_blue",
        displayName = "Slate Blue",
        primary = Color(0xFF425864),
        primaryLight = Color(0xFF647E8D),
        primarySubtle = Color(0xFF212C32),
        onPrimary = Color(0xFF000000),
        surface = Color(0xFF0C0F12),
        surfaceVariant = Color(0xFF12161A),
        surfaceTile = Color(0xFF24282D),
        outline = Color(0xFF1D2429),
        textPrimary = Color(0xFF99C2DB),
        textMuted = Color(0xFF6F8FA1),
        textDark = Color(0xFF465F6E)
    ),
    DUSKY_PURPLE(
        id = "dusky_purple",
        displayName = "Dusky Purple",
        primary = Color(0xFF574B5A),
        primaryLight = Color(0xFF7B6C7E),
        primarySubtle = Color(0xFF2C262D),
        onPrimary = Color(0xFF000000),
        surface = Color(0xFF100D12),
        surfaceVariant = Color(0xFF17131A),
        surfaceTile = Color(0xFF2A242B),
        outline = Color(0xFF251E27),
        textPrimary = Color(0xFFCEACDA),
        textMuted = Color(0xFF997FA3),
        textDark = Color(0xFF634F6B)
    ),
    BURNT_ORANGE(
        id = "burnt_orange",
        displayName = "Burnt Orange",
        primary = Color(0xFFA46338),
        primaryLight = Color(0xFFC68254),
        primarySubtle = Color(0xFF52311C),
        onPrimary = Color(0xFF000000),
        surface = Color(0xFF120E0B),
        surfaceVariant = Color(0xFF1A1410),
        surfaceTile = Color(0xFF2D2723),
        outline = Color(0xFF2C201A),
        textPrimary = Color(0xFFE9AD85),
        textMuted = Color(0xFFB67C55),
        textDark = Color(0xFF7A4B29)
    ),
    FOREST_GREEN(
        id = "forest_green",
        displayName = "Forest Green",
        primary = Color(0xFF4F5E45),
        primaryLight = Color(0xFF738666),
        primarySubtle = Color(0xFF272F22),
        onPrimary = Color(0xFF000000),
        surface = Color(0xFF0D100C),
        surfaceVariant = Color(0xFF131812),
        surfaceTile = Color(0xFF242D24),
        outline = Color(0xFF1E271D),
        textPrimary = Color(0xFFB2D4A2),
        textMuted = Color(0xFF7E9C6E),
        textDark = Color(0xFF4E6442)
    ),
    PARCHMENT_WHITE(
        id = "parchment_white",
        displayName = "Parchment White",
        primary = Color(0xFFECE9DF),
        primaryLight = Color(0xFFF2ECE1),
        primarySubtle = Color(0xFF3A3935),
        onPrimary = Color(0xFF000000),
        surface = Color(0xFF10100F),
        surfaceVariant = Color(0xFF181816),
        surfaceTile = Color(0xFF282826),
        outline = Color(0xFF262624),
        textPrimary = Color(0xFFE8E1CD),
        textMuted = Color(0xFFA19985),
        textDark = Color(0xFF6B6555)
    );

    companion object {
        private const val PREFS_NAME = "minesweeper_prefs"
        private const val KEY_THEME = "selected_color_theme"

        fun getSelectedTheme(context: Context): AppColorTheme {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedId = prefs.getString(KEY_THEME, OLIVE_GOLD.id)
            return entries.firstOrNull { it.id == savedId } ?: OLIVE_GOLD
        }

        fun setSelectedTheme(context: Context, theme: AppColorTheme) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_THEME, theme.id).apply()
        }
    }
}
