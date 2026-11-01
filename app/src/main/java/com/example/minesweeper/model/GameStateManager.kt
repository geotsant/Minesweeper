package com.example.minesweeper.model

import android.content.Context
import android.util.Base64
import com.example.minesweeper.core.MinesweeperEngine

object GameStateManager {
    private const val PREFS_NAME = "minesweeper_active_game"
    private const val KEY_HAS_ACTIVE = "has_active_game"
    private const val KEY_ROWS = "rows"
    private const val KEY_COLS = "cols"
    private const val KEY_MINES = "mines"
    private const val KEY_STATUS = "status"
    private const val KEY_FIRST_MOVE = "first_move"
    private const val KEY_ELAPSED = "elapsed_seconds"
    private const val KEY_BOARD_BASE64 = "board_base64"

    fun saveGame(context: Context, engine: MinesweeperEngine) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val status = engine.getGameStatus()
        val hasActive = (status == MinesweeperEngine.STATUS_NOT_STARTED || status == MinesweeperEngine.STATUS_PLAYING)

        if (!hasActive || engine.getRows() <= 0 || engine.getCols() <= 0) {
            prefs.edit().putBoolean(KEY_HAS_ACTIVE, false).apply()
            return
        }

        val rows = engine.getRows()
        val cols = engine.getCols()
        val mines = engine.getTotalMines()
        val firstMove = engine.isFirstMove()
        val elapsed = engine.getElapsedSeconds(System.currentTimeMillis())
        val boardBytes = ByteArray(rows * cols)
        engine.syncBoard(boardBytes)
        val base64 = Base64.encodeToString(boardBytes, Base64.NO_WRAP)

        prefs.edit()
            .putBoolean(KEY_HAS_ACTIVE, true)
            .putInt(KEY_ROWS, rows)
            .putInt(KEY_COLS, cols)
            .putInt(KEY_MINES, mines)
            .putInt(KEY_STATUS, status)
            .putBoolean(KEY_FIRST_MOVE, firstMove)
            .putInt(KEY_ELAPSED, elapsed)
            .putString(KEY_BOARD_BASE64, base64)
            .apply()
    }

    fun hasSavedGame(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAS_ACTIVE, false)
    }

    fun loadGame(context: Context, engine: MinesweeperEngine): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_ACTIVE, false)) return false

        val rows = prefs.getInt(KEY_ROWS, GameDifficulty.BOARD_ROWS)
        val cols = prefs.getInt(KEY_COLS, GameDifficulty.BOARD_COLS)
        val mines = prefs.getInt(KEY_MINES, GameDifficulty.BOARD_MINES)
        if (rows != GameDifficulty.BOARD_ROWS || cols != GameDifficulty.BOARD_COLS || mines != GameDifficulty.BOARD_MINES) {
            clearSavedGame(context)
            engine.initGame(GameDifficulty.BOARD_ROWS, GameDifficulty.BOARD_COLS, GameDifficulty.BOARD_MINES)
            return false
        }
        val status = prefs.getInt(KEY_STATUS, MinesweeperEngine.STATUS_NOT_STARTED)
        val firstMove = prefs.getBoolean(KEY_FIRST_MOVE, true)
        val elapsed = prefs.getInt(KEY_ELAPSED, 0)
        val base64 = prefs.getString(KEY_BOARD_BASE64, null)

        val boardBytes = if (!base64.isNullOrEmpty()) {
            try {
                Base64.decode(base64, Base64.NO_WRAP)
            } catch (e: Exception) {
                ByteArray(rows * cols)
            }
        } else {
            ByteArray(rows * cols)
        }

        if (firstMove || boardBytes.size != (rows * cols)) {
            engine.initGame(rows, cols, mines)
            return true
        }

        engine.restoreGame(rows, cols, mines, status, firstMove, elapsed, boardBytes, System.currentTimeMillis())
        return true
    }

    fun clearSavedGame(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HAS_ACTIVE, false).apply()
    }
}
