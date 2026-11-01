package com.example.minesweeper

import com.example.minesweeper.core.MinesweeperEngine
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun testWinOnlyWhenAllMinesCorrectlyMarked() {
        val engine = MinesweeperEngine()
        val rows = 3
        val cols = 3
        val mines = 2
        val totalCells = rows * cols

        // Construct board: mines at indices 0 and 1 (value 9), safe cells at 2..8 (value 0, revealed)
        val board = ByteArray(totalCells)
        board[0] = MinesweeperEngine.VALUE_MINE.toByte()
        board[1] = MinesweeperEngine.VALUE_MINE.toByte()
        for (i in 2 until totalCells) {
            board[i] = MinesweeperEngine.FLAG_REVEALED.toByte()
        }

        engine.restoreGame(rows, cols, mines, MinesweeperEngine.STATUS_PLAYING, false, 0, board)
        engine.resumeGame()

        // All safe cells are revealed, but neither mine is flagged: MUST NOT BE WON
        assertEquals(MinesweeperEngine.STATUS_PLAYING, engine.getGameStatus())

        // Flag the first mine at (0, 0)
        engine.handleTap(0, 0, 1000)
        assertEquals(1, engine.getMinesRemaining())
        assertEquals(MinesweeperEngine.STATUS_PLAYING, engine.getGameStatus()) // Still playing!

        // Flag the second mine at (0, 1)
        engine.handleTap(0, 1, 2000)
        assertEquals(0, engine.getMinesRemaining())
        // Both mines are now correctly marked!
        assertEquals(MinesweeperEngine.STATUS_WON, engine.getGameStatus())
    }

    @Test
    fun testNoWinOnWrongFlag() {
        val engine = MinesweeperEngine()
        val rows = 3
        val cols = 3
        val mines = 1
        val totalCells = rows * cols

        val board = ByteArray(totalCells)
        board[0] = MinesweeperEngine.VALUE_MINE.toByte() // Mine at (0, 0)
        // Others are safe, unrevealed

        engine.restoreGame(rows, cols, mines, MinesweeperEngine.STATUS_PLAYING, false, 0, board)
        engine.resumeGame()

        // Flag a non-mine at (0, 1)
        engine.handleTap(0, 1, 1000)
        assertEquals(0, engine.getMinesRemaining()) // 1 flag placed, so counter says 0
        // BUT it is a wrong flag, so it MUST NOT WIN!
        assertEquals(MinesweeperEngine.STATUS_PLAYING, engine.getGameStatus())
    }

    @Test
    fun testGenerateNoGuessBoardTiming() {
        val testPositions = listOf(
            Pair(0, 0), Pair(0, 8), Pair(0, 17),
            Pair(10, 0), Pair(16, 9), Pair(20, 17),
            Pair(31, 0), Pair(31, 9), Pair(31, 17), Pair(5, 5)
        )
        for ((idx, pos) in testPositions.withIndex()) {
            val start = System.currentTimeMillis()
            val solver = com.example.minesweeper.core.NoGuessSolver(32, 18)
            val mines = com.example.minesweeper.core.NoGuessSolver.generateNoGuessBoard(
                32, 18, 150, pos.first, pos.second, kotlin.random.Random(12345 + idx * 7)
            )
            val elapsed = System.currentTimeMillis() - start
            val solvable = solver.resetAndSolve(150, mines, pos.first, pos.second)
            val openingSize = solver.initialRevealed
            val mineCount = mines.count { it }
            println("Pos $pos ($idx): Generated in ${elapsed}ms, opening=$openingSize cells, solvable=$solvable, mines=$mineCount")
            assertEquals(150, mineCount)
            assertTrue("Board must be 100% solvable with no 50/50!", solvable)
            assertTrue("Opening must be a small chunk (<= 18 cells)!", openingSize <= 18)
        }
    }

    @Test
    fun testBoardDimensionsAndMineCount() {
        val engine = MinesweeperEngine()
        assertEquals(32, engine.getRows())
        assertEquals(18, engine.getCols())
        assertEquals(150, engine.getTotalMines())
        assertEquals(150, engine.getMinesRemaining())

        assertEquals(32, com.example.minesweeper.model.GameDifficulty.BOARD_ROWS)
        assertEquals(18, com.example.minesweeper.model.GameDifficulty.BOARD_COLS)
        assertEquals(150, com.example.minesweeper.model.GameDifficulty.BOARD_MINES)

        engine.initGame()
        assertEquals(32, engine.getRows())
        assertEquals(18, engine.getCols())
        assertEquals(150, engine.getTotalMines())
    }

    @Test
    fun testAppColorThemes() {
        val themes = com.example.minesweeper.model.AppColorTheme.entries
        assertEquals(7, themes.size)

        val olive = com.example.minesweeper.model.AppColorTheme.OLIVE_GOLD
        assertEquals(0xFF85804C.toInt(), (olive.primary.value shr 32).toInt())

        val terracotta = com.example.minesweeper.model.AppColorTheme.TERRACOTTA_RED
        assertEquals(0xFF8C473A.toInt(), (terracotta.primary.value shr 32).toInt())

        val slate = com.example.minesweeper.model.AppColorTheme.SLATE_BLUE
        assertEquals(0xFF425864.toInt(), (slate.primary.value shr 32).toInt())

        val dusky = com.example.minesweeper.model.AppColorTheme.DUSKY_PURPLE
        assertEquals(0xFF574B5A.toInt(), (dusky.primary.value shr 32).toInt())

        val burnt = com.example.minesweeper.model.AppColorTheme.BURNT_ORANGE
        assertEquals(0xFFA46338.toInt(), (burnt.primary.value shr 32).toInt())

        val forest = com.example.minesweeper.model.AppColorTheme.FOREST_GREEN
        assertEquals(0xFF4F5E45.toInt(), (forest.primary.value shr 32).toInt())

        val parchment = com.example.minesweeper.model.AppColorTheme.PARCHMENT_WHITE
        assertEquals(0xFFECE9DF.toInt(), (parchment.primary.value shr 32).toInt())

        for (theme in themes) {
            assertEquals("onPrimary for ${theme.displayName} must be Black (0xFF000000)", 0xFF000000.toInt(), (theme.onPrimary.value shr 32).toInt())
            assertNotEquals("textPrimary for ${theme.displayName} must not be white", 0xFFFFFFFF.toInt(), (theme.textPrimary.value shr 32).toInt())
            assertNotEquals("primaryLight for ${theme.displayName} must not be white", 0xFFFFFFFF.toInt(), (theme.primaryLight.value shr 32).toInt())
        }

        assertEquals(0xFFE2DC96.toInt(), (olive.textPrimary.value shr 32).toInt())
        assertEquals(0xFFE89F96.toInt(), (terracotta.textPrimary.value shr 32).toInt())
        assertEquals(0xFF99C2DB.toInt(), (slate.textPrimary.value shr 32).toInt())
        assertEquals(0xFFCEACDA.toInt(), (dusky.textPrimary.value shr 32).toInt())
        assertEquals(0xFFE9AD85.toInt(), (burnt.textPrimary.value shr 32).toInt())
        assertEquals(0xFFB2D4A2.toInt(), (forest.textPrimary.value shr 32).toInt())
        assertEquals(0xFFE8E1CD.toInt(), (parchment.textPrimary.value shr 32).toInt())
    }
}