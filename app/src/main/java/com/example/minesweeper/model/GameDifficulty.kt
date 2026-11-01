package com.example.minesweeper.model

import android.content.Context

enum class GameDifficulty(
    val rows: Int,
    val cols: Int,
    val mines: Int,
    val displayName: String
) {
    STANDARD(32, 18, 150, "Standard (150 Mines)");

    companion object {
        const val BOARD_ROWS = 32
        const val BOARD_COLS = 18
        const val BOARD_MINES = 150

        private const val PREFS_NAME = "minesweeper_prefs"
        private const val KEY_LONG_PRESS_MS = "long_press_ms"

        fun getSelectedDifficulty(context: Context): GameDifficulty = STANDARD

        fun setSelectedDifficulty(context: Context, difficulty: GameDifficulty) {}

        fun getLongPressDuration(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_LONG_PRESS_MS, 250).toLong()
        }

        fun setLongPressDuration(context: Context, ms: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_LONG_PRESS_MS, ms).apply()
        }
    }
}
