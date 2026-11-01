package com.example.minesweeper.core

import com.example.minesweeper.model.GameDifficulty
import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Pure Kotlin Minesweeper game engine.
 * Ultra-fast, zero-allocation, lightweight engine with 100% deterministic solvability.
 */
class MinesweeperEngine {

    companion object {
        const val STATUS_NOT_STARTED = 0
        const val STATUS_PLAYING = 1
        const val STATUS_WON = 2
        const val STATUS_LOST = 3

        const val MASK_VALUE = 0x0F
        const val FLAG_REVEALED = 0x10
        const val FLAG_FLAGGED = 0x20
        const val FLAG_EXPLODED = 0x40
        const val FLAG_WRONG_FLAG = 0x80
        const val VALUE_MINE = 9
    }

    private var rows = GameDifficulty.BOARD_ROWS
    private var cols = GameDifficulty.BOARD_COLS
    private var totalMines = GameDifficulty.BOARD_MINES
    private var status = STATUS_NOT_STARTED
    private var firstMove = true
    private var flaggedCount = 0
    private var revealedCount = 0
    private var startTimeMs: Long = 0
    private var endTimeMs: Long = 0

    private var boardData = ByteArray(rows * cols)
    private var isMine = BooleanArray(rows * cols)

    private var isPaused = false
    private var pausedElapsedSeconds = 0

    fun isUsingRust(): Boolean = false

    fun isPaused(): Boolean = isPaused

    fun pauseGame(currentTimeMs: Long = System.currentTimeMillis()) {
        val st = getGameStatus()
        if (st == STATUS_PLAYING && !isPaused) {
            pausedElapsedSeconds = getElapsedSeconds(currentTimeMs)
            isPaused = true
        }
    }

    fun resumeGame(currentTimeMs: Long = System.currentTimeMillis()) {
        val st = getGameStatus()
        if (st == STATUS_PLAYING && isPaused) {
            isPaused = false
            setElapsedSeconds(pausedElapsedSeconds, currentTimeMs)
        }
    }

    fun setElapsedSeconds(elapsedSec: Int, currentTimeMs: Long = System.currentTimeMillis()) {
        pausedElapsedSeconds = elapsedSec
        startTimeMs = currentTimeMs - (elapsedSec * 1000L)
    }

    fun initGame(
        r: Int = GameDifficulty.BOARD_ROWS,
        c: Int = GameDifficulty.BOARD_COLS,
        m: Int = GameDifficulty.BOARD_MINES,
        seed: Long = System.currentTimeMillis()
    ) {
        isPaused = false
        pausedElapsedSeconds = 0
        rows = r
        cols = c
        val totalCells = r * c
        totalMines = m.coerceIn(1, (totalCells - 1).coerceAtLeast(1))
        boardData = ByteArray(totalCells)
        isMine = BooleanArray(totalCells)
        status = STATUS_NOT_STARTED
        firstMove = true
        flaggedCount = 0
        revealedCount = 0
        startTimeMs = 0
        endTimeMs = 0
    }

    fun resetGame(seed: Long = System.currentTimeMillis()) {
        isPaused = false
        pausedElapsedSeconds = 0
        initGame(rows, cols, totalMines, seed)
    }

    fun handleTap(row: Int, col: Int, currentTimeMs: Long): Boolean {
        if (status == STATUS_WON || status == STATUS_LOST) return false
        if (row !in 0 until rows || col !in 0 until cols) return false

        val idx = row * cols + col

        if (firstMove) {
            startGameWithFirstClick(row, col, currentTimeMs)
            return true
        }

        val b = boardData[idx].toInt() and 0xFF
        val isRevealed = (b and FLAG_REVEALED) != 0

        if (isRevealed) {
            val num = b and MASK_VALUE
            if (num in 1..8) {
                return chord(row, col, currentTimeMs)
            }
            return false
        }

        toggleFlag(idx)
        checkWin(currentTimeMs)
        return true
    }

    fun handleLongPress(row: Int, col: Int, currentTimeMs: Long): Boolean {
        if (status == STATUS_WON || status == STATUS_LOST) return false
        if (row !in 0 until rows || col !in 0 until cols) return false

        if (firstMove) {
            startGameWithFirstClick(row, col, currentTimeMs)
            return true
        }

        val idx = row * cols + col
        val b = boardData[idx].toInt() and 0xFF
        val isRevealed = (b and FLAG_REVEALED) != 0
        val isFlagged = (b and FLAG_FLAGGED) != 0

        if (isRevealed || isFlagged) return false

        return revealCell(row, col, currentTimeMs)
    }

    fun chord(row: Int, col: Int, currentTimeMs: Long): Boolean {
        val idx = row * cols + col
        val b = boardData[idx].toInt() and 0xFF
        val isRevealed = (b and FLAG_REVEALED) != 0
        val num = b and MASK_VALUE
        if (!isRevealed || num !in 1..8) return false

        var flagCount = 0
        val unrevealedNeighbors = ArrayList<Int>(8)

        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = row + dr
                val nc = col + dc
                if (nr in 0 until rows && nc in 0 until cols) {
                    val nIdx = nr * cols + nc
                    val nb = boardData[nIdx].toInt() and 0xFF
                    if ((nb and FLAG_FLAGGED) != 0) {
                        flagCount++
                    } else if ((nb and FLAG_REVEALED) == 0) {
                        unrevealedNeighbors.add(nIdx)
                    }
                }
            }
        }

        if (flagCount == num && unrevealedNeighbors.isNotEmpty()) {
            var changed = false
            var hitMine = false

            for (nIdx in unrevealedNeighbors) {
                if (isMine[nIdx]) {
                    boardData[nIdx] = (boardData[nIdx].toInt() or FLAG_EXPLODED).toByte()
                    hitMine = true
                } else {
                    boardData[nIdx] = (boardData[nIdx].toInt() or FLAG_REVEALED).toByte()
                    revealedCount++
                    changed = true
                    val nNum = boardData[nIdx].toInt() and MASK_VALUE
                    if (nNum == 0) {
                        cascadeFloodFill(nIdx / cols, nIdx % cols)
                    }
                }
            }

            if (hitMine) {
                handleLoss(currentTimeMs)
                return true
            }

            checkWin(currentTimeMs)
            return changed
        }

        return false
    }

    fun getRows(): Int = rows
    fun getCols(): Int = cols
    fun getTotalMines(): Int = totalMines
    fun getMinesRemaining(): Int = (totalMines - flaggedCount)
    fun getGameStatus(): Int = status
    fun isFirstMove(): Boolean = firstMove

    fun getElapsedSeconds(currentTimeMs: Long = System.currentTimeMillis()): Int {
        if (status == STATUS_NOT_STARTED) return 0
        if (status == STATUS_WON || status == STATUS_LOST) {
            return ((endTimeMs - startTimeMs).coerceAtLeast(0) / 1000).toInt()
        }
        if (isPaused) {
            return pausedElapsedSeconds
        }
        return ((currentTimeMs - startTimeMs).coerceAtLeast(0) / 1000).toInt()
    }

    fun getCellPacked(r: Int, c: Int): Int {
        return boardData[r * cols + c].toInt() and 0xFF
    }

    fun syncBoard(target: ByteArray): Int {
        val len = minOf(boardData.size, target.size)
        System.arraycopy(boardData, 0, target, 0, len)
        return len
    }

    fun restoreGame(
        r: Int,
        c: Int,
        m: Int,
        st: Int,
        first: Boolean,
        elapsedSec: Int,
        boardBytes: ByteArray,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        rows = r
        cols = c
        totalMines = m
        status = st
        firstMove = first
        isPaused = true
        pausedElapsedSeconds = elapsedSec
        startTimeMs = currentTimeMs - (elapsedSec * 1000L)
        endTimeMs = if (st == STATUS_WON || st == STATUS_LOST) currentTimeMs else 0

        val totalCells = r * c
        boardData = boardBytes.copyOf()
        if (isMine.size != totalCells) {
            isMine = BooleanArray(totalCells)
        }
        flaggedCount = 0
        revealedCount = 0
        for (i in 0 until minOf(boardBytes.size, totalCells)) {
            val b = boardBytes[i].toInt() and 0xFF
            isMine[i] = (b and MASK_VALUE) == VALUE_MINE
            if ((b and FLAG_FLAGGED) != 0) flaggedCount++
            if ((b and FLAG_REVEALED) != 0) revealedCount++
        }
        return true
    }

    private fun toggleFlag(idx: Int) {
        val b = boardData[idx].toInt() and 0xFF
        if ((b and FLAG_REVEALED) != 0) return

        if ((b and FLAG_FLAGGED) != 0) {
            boardData[idx] = (b and FLAG_FLAGGED.inv()).toByte()
            flaggedCount = (flaggedCount - 1).coerceAtLeast(0)
        } else {
            boardData[idx] = (b or FLAG_FLAGGED).toByte()
            flaggedCount++
        }
    }

    private fun revealCell(r: Int, c: Int, currentTimeMs: Long): Boolean {
        val idx = r * cols + c
        if (isMine[idx]) {
            boardData[idx] = (boardData[idx].toInt() or FLAG_EXPLODED).toByte()
            handleLoss(currentTimeMs)
            return true
        }

        boardData[idx] = (boardData[idx].toInt() or FLAG_REVEALED).toByte()
        revealedCount++

        val num = boardData[idx].toInt() and MASK_VALUE
        if (num == 0) {
            cascadeFloodFill(r, c)
        }

        checkWin(currentTimeMs)
        return true
    }

    private fun startGameWithFirstClick(safeR: Int, safeC: Int, currentTimeMs: Long) {
        startTimeMs = currentTimeMs
        status = STATUS_PLAYING
        firstMove = false

        val totalCells = rows * cols
        val generatedMines = NoGuessSolver.generateNoGuessBoard(
            rows, cols, totalMines, safeR, safeC, Random(currentTimeMs)
        )
        isMine = generatedMines

        for (i in 0 until totalCells) {
            boardData[i] = if (isMine[i]) VALUE_MINE.toByte() else 0.toByte()
        }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                if (isMine[idx]) continue
                var count = 0
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until rows && nc in 0 until cols && isMine[nr * cols + nc]) {
                            count++
                        }
                    }
                }
                boardData[idx] = count.toByte()
            }
        }

        val startIdx = safeR * cols + safeC
        boardData[startIdx] = (boardData[startIdx].toInt() or FLAG_REVEALED).toByte()
        revealedCount = 1

        val startNum = boardData[startIdx].toInt() and MASK_VALUE
        if (startNum == 0) {
            cascadeFloodFill(safeR, safeC)
        }

        checkWin(currentTimeMs)
    }

    private fun cascadeFloodFill(startR: Int, startC: Int) {
        val queue = ArrayDeque<Int>()
        queue.add(startR * cols + startC)

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            val r = idx / cols
            val c = idx % cols

            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in 0 until rows && nc in 0 until cols) {
                        val nIdx = nr * cols + nc
                        val nb = boardData[nIdx].toInt() and 0xFF
                        val isRev = (nb and FLAG_REVEALED) != 0
                        val isFlg = (nb and FLAG_FLAGGED) != 0
                        if (!isRev && !isFlg && !isMine[nIdx]) {
                            boardData[nIdx] = (nb or FLAG_REVEALED).toByte()
                            revealedCount++
                            val num = boardData[nIdx].toInt() and MASK_VALUE
                            if (num == 0) {
                                queue.add(nIdx)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleLoss(currentTimeMs: Long) {
        status = STATUS_LOST
        endTimeMs = currentTimeMs

        for (i in 0 until (rows * cols)) {
            val b = boardData[i].toInt() and 0xFF
            val isFlg = (b and FLAG_FLAGGED) != 0
            if (isMine[i]) {
                if (!isFlg) {
                    boardData[i] = (b or FLAG_REVEALED or VALUE_MINE).toByte()
                }
            } else if (isFlg) {
                boardData[i] = (b or FLAG_WRONG_FLAG).toByte()
            }
        }
    }

    private fun checkWin(currentTimeMs: Long) {
        if (status != STATUS_PLAYING) return
        val totalCells = rows * cols

        // The game should end only when all mines are correctly marked (zero missing, zero wrong)
        if (flaggedCount != totalMines) return

        for (i in 0 until totalCells) {
            val isFlg = (boardData[i].toInt() and FLAG_FLAGGED) != 0
            if (isMine[i] != isFlg) {
                return
            }
        }

        status = STATUS_WON
        endTimeMs = currentTimeMs

        // Reveal any remaining safe cells
        val safeCells = totalCells - totalMines
        for (i in 0 until totalCells) {
            if (!isMine[i]) {
                val b = boardData[i].toInt() and 0xFF
                if ((b and FLAG_REVEALED) == 0) {
                    boardData[i] = (b or FLAG_REVEALED).toByte()
                }
            }
        }
        revealedCount = safeCells
    }
}
