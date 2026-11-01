package com.example.minesweeper.model

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GameScore(
    val timeSeconds: Int,
    val dateFormatted: String
)

object LeaderboardManager {
    private const val PREFS_NAME = "minesweeper_scores"
    private const val KEY_TOP_SCORES = "top_10_scores"

    fun getTopScores(context: Context): List<GameScore> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TOP_SCORES, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 2) {
                val time = parts[0].toIntOrNull()
                val date = parts[1]
                if (time != null && time > 0) GameScore(time, date) else null
            } else null
        }.sortedBy { it.timeSeconds }.take(10)
    }

    fun recordGameWin(context: Context, timeSeconds: Int): Boolean {
        if (timeSeconds <= 0) return false
        val current = getTopScores(context).toMutableList()

        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date())
        current.add(GameScore(timeSeconds, dateStr))

        val top10 = current.sortedBy { it.timeSeconds }.take(10)
        val serialized = top10.joinToString(";") { "${it.timeSeconds}|${it.dateFormatted}" }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOP_SCORES, serialized).apply()

        return top10.any { it.timeSeconds == timeSeconds && it.dateFormatted == dateStr }
    }
}
