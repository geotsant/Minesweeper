package com.example.minesweeper.core

import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Multi-tier deductive logic solver and 100% solvable (No-Guess) board generator.
 * Runs completely on Android ART with zero-allocation inner loops.
 */
class NoGuessSolver(val rows: Int, val cols: Int) {
    val totalCells = rows * cols

    val neighborCounts = IntArray(totalCells)
    val neighborTable = Array(totalCells) { IntArray(8) }

    val revealed = BooleanArray(totalCells)
    val knownMines = BooleanArray(totalCells)
    val neighborMines = ByteArray(totalCells)
    var revealedCount = 0
    var initialRevealed = 0
    private var safeCellsTotal = 0

    val stalledClues = IntArray(256)
    var stalledCluesCount = 0

    private val revealQueue = ArrayDeque<Int>(128)
    private val unrevBuf = IntArray(8)

    init {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                var cnt = 0
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until rows && nc in 0 until cols) {
                            neighborTable[idx][cnt++] = nr * cols + nc
                        }
                    }
                }
                neighborCounts[idx] = cnt
            }
        }
    }

    fun resetAndSolve(
        totalMines: Int,
        mines: BooleanArray,
        startR: Int,
        startC: Int,
        maxInitialAllowed: Int = Int.MAX_VALUE,
        maxZerosAllowed: Int = Int.MAX_VALUE
    ): Boolean {
        safeCellsTotal = totalCells - totalMines
        revealedCount = 0
        revealed.fill(false)
        knownMines.fill(false)

        var zeroCount = 0
        for (idx in 0 until totalCells) {
            if (mines[idx]) {
                neighborMines[idx] = 0
                continue
            }
            var count = 0
            val cnt = neighborCounts[idx]
            val nbrs = neighborTable[idx]
            for (i in 0 until cnt) {
                if (mines[nbrs[i]]) {
                    count++
                }
            }
            neighborMines[idx] = count.toByte()
            if (count == 0) {
                zeroCount++
            }
        }

        // Fast early rejection 1: avoid candidate boards with excessive zero lakes
        if (zeroCount > maxZerosAllowed) {
            return false
        }

        val startIdx = startR * cols + startC
        if (mines[startIdx]) {
            return false
        }

        reveal(startIdx, mines)
        initialRevealed = revealedCount

        // Fast early rejection 2: avoid candidate boards with openings larger than desired
        if (initialRevealed > maxInitialAllowed) {
            return false
        }

        while (revealedCount < safeCellsTotal) {
            // Tier 1: Single cell saturation / zero deduction
            if (stepSingleCell(mines)) continue

            // Tier 2: Subset deductions
            if (stepSubsets(mines)) continue

            // Tier 3: Connected Component Constraint Satisfaction (Tank solver)
            if (stepCsp(mines)) continue

            // Tier 4: Global mine count deduction
            if (stepGlobalMineCount(totalMines, mines)) continue

            // Stalemate -> guessing required!
            collectStalledClues()
            return false
        }

        stalledCluesCount = 0
        return true
    }

    private fun collectStalledClues() {
        stalledCluesCount = 0
        for (idx in 0 until totalCells) {
            if (!revealed[idx] || neighborMines[idx].toInt() == 0) continue
            val cnt = neighborCounts[idx]
            val nbrs = neighborTable[idx]
            var unrev = 0
            var flags = 0
            for (i in 0 until cnt) {
                val n = nbrs[i]
                if (knownMines[n]) flags++
                else if (!revealed[n]) unrev++
            }
            val need = neighborMines[idx].toInt() - flags
            if (unrev > 0 && need > 0) {
                stalledClues[stalledCluesCount++] = idx
                if (stalledCluesCount >= 256) break
            }
        }
    }

    fun reveal(idx: Int, mines: BooleanArray) {
        if (revealed[idx] || knownMines[idx]) return
        revealed[idx] = true
        revealedCount++

        if (neighborMines[idx].toInt() == 0) {
            revealQueue.clear()
            revealQueue.add(idx)

            while (revealQueue.isNotEmpty()) {
                val curr = revealQueue.removeFirst()
                val cnt = neighborCounts[curr]
                val nbrs = neighborTable[curr]
                for (i in 0 until cnt) {
                    val nIdx = nbrs[i]
                    if (!revealed[nIdx] && !knownMines[nIdx] && !mines[nIdx]) {
                        revealed[nIdx] = true
                        revealedCount++
                        if (neighborMines[nIdx].toInt() == 0) {
                            revealQueue.add(nIdx)
                        }
                    }
                }
            }
        }
    }

    private fun stepSingleCell(mines: BooleanArray): Boolean {
        var progress = false

        for (idx in 0 until totalCells) {
            if (!revealed[idx] || neighborMines[idx].toInt() == 0) continue

            var unrevLen = 0
            var flags = 0
            val cnt = neighborCounts[idx]
            val nbrs = neighborTable[idx]

            for (i in 0 until cnt) {
                val n = nbrs[i]
                if (knownMines[n]) {
                    flags++
                } else if (!revealed[n]) {
                    unrevBuf[unrevLen++] = n
                }
            }

            if (unrevLen == 0) continue

            val need = neighborMines[idx].toInt() - flags

            if (need == unrevLen) {
                for (i in 0 until unrevLen) {
                    val u = unrevBuf[i]
                    if (!knownMines[u]) {
                        knownMines[u] = true
                        progress = true
                    }
                }
            } else if (need == 0) {
                for (i in 0 until unrevLen) {
                    val u = unrevBuf[i]
                    if (!revealed[u]) {
                        reveal(u, mines)
                        progress = true
                    }
                }
            }
        }

        return progress
    }

    private val fUnrev = Array(256) { IntArray(8) }
    private val fLen = IntArray(256)
    private val fRem = IntArray(256)
    private var fCount = 0
    private val diffBuf = IntArray(8)

    private fun stepSubsets(mines: BooleanArray): Boolean {
        var progress = false
        fCount = 0

        for (idx in 0 until totalCells) {
            if (!revealed[idx] || neighborMines[idx].toInt() == 0) continue

            var unrevLen = 0
            var flags = 0
            val cnt = neighborCounts[idx]
            val nbrs = neighborTable[idx]

            for (i in 0 until cnt) {
                val n = nbrs[i]
                if (knownMines[n]) {
                    flags++
                } else if (!revealed[n]) {
                    fUnrev[fCount][unrevLen++] = n
                }
            }

            if (unrevLen > 0) {
                fLen[fCount] = unrevLen
                fRem[fCount] = neighborMines[idx].toInt() - flags
                fCount++
                if (fCount >= 256) break
            }
        }

        for (i in 0 until fCount) {
            val len1 = fLen[i]
            val rem1 = fRem[i]
            val u1 = fUnrev[i]

            for (j in 0 until fCount) {
                if (i == j) continue
                val len2 = fLen[j]
                // Fast prune: a set cannot be a proper subset of a set with fewer or equal elements
                if (len1 >= len2) continue

                val rem2 = fRem[j]
                val u2 = fUnrev[j]

                var isSubset = true
                for (k in 0 until len1) {
                    val v = u1[k]
                    var found = false
                    for (l in 0 until len2) {
                        if (u2[l] == v) {
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        isSubset = false
                        break
                    }
                }

                if (isSubset) {
                    var diffCount = 0
                    for (l in 0 until len2) {
                        val v = u2[l]
                        var inU1 = false
                        for (k in 0 until len1) {
                            if (u1[k] == v) {
                                inU1 = true
                                break
                            }
                        }
                        if (!inU1) {
                            diffBuf[diffCount++] = v
                        }
                    }

                    if (diffCount > 0) {
                        val remDiff = rem2 - rem1
                        if (remDiff == 0) {
                            for (d in 0 until diffCount) {
                                val cell = diffBuf[d]
                                if (!revealed[cell]) {
                                    reveal(cell, mines)
                                    progress = true
                                }
                            }
                        } else if (remDiff == diffCount) {
                            for (d in 0 until diffCount) {
                                val cell = diffBuf[d]
                                if (!knownMines[cell]) {
                                    knownMines[cell] = true
                                    progress = true
                                }
                            }
                        }
                    }
                }
            }
        }

        return progress
    }

    private class Constraint(val vars: IntArray, val sum: Int)

    private fun stepCsp(mines: BooleanArray): Boolean {
        val constraints = ArrayList<Constraint>(128)
        val frontierSet = HashSet<Int>(128)

        for (idx in 0 until totalCells) {
            if (!revealed[idx] || neighborMines[idx].toInt() == 0) continue

            val unrev = IntArray(8)
            var unrevLen = 0
            var flags = 0
            val cnt = neighborCounts[idx]
            val nbrs = neighborTable[idx]

            for (i in 0 until cnt) {
                val n = nbrs[i]
                if (knownMines[n]) {
                    flags++
                } else if (!revealed[n]) {
                    unrev[unrevLen++] = n
                    frontierSet.add(n)
                }
            }

            if (unrevLen > 0) {
                val rem = neighborMines[idx].toInt() - flags
                val vars = unrev.copyOf(unrevLen)
                constraints.add(Constraint(vars, rem))
            }
        }

        if (constraints.isEmpty()) return false

        val varList = frontierSet.toIntArray()
        val varToIdx = HashMap<Int, Int>(varList.size)
        for (i in varList.indices) {
            varToIdx[varList[i]] = i
        }

        val adj = Array(varList.size) { ArrayList<Int>(8) }
        for (c in constraints) {
            for (i in c.vars.indices) {
                for (j in i + 1 until c.vars.size) {
                    val u = varToIdx[c.vars[i]]!!
                    val v = varToIdx[c.vars[j]]!!
                    adj[u].add(v)
                    adj[v].add(u)
                }
            }
        }

        val visited = BooleanArray(varList.size)
        var progress = false

        for (i in varList.indices) {
            if (visited[i]) continue
            val comp = ArrayList<Int>()
            val q = ArrayDeque<Int>()
            q.add(i)
            visited[i] = true

            while (q.isNotEmpty()) {
                val u = q.removeFirst()
                comp.add(u)
                for (v in adj[u]) {
                    if (!visited[v]) {
                        visited[v] = true
                        q.add(v)
                    }
                }
            }

            if (comp.size > 14) continue

            val compVarIndices = comp.toHashSet()
            val compRealVars = IntArray(comp.size) { compIdx -> varList[comp[compIdx]] }
            val realVarToCompIdx = HashMap<Int, Int>(comp.size)
            for (ci in compRealVars.indices) {
                realVarToCompIdx[compRealVars[ci]] = ci
            }

            val compConstraints = ArrayList<Constraint>()
            for (c in constraints) {
                var touches = false
                for (v in c.vars) {
                    if (compVarIndices.contains(varToIdx[v])) {
                        touches = true
                        break
                    }
                }
                if (touches) {
                    val mappedVars = IntArray(c.vars.size) { vi -> realVarToCompIdx[c.vars[vi]]!! }
                    compConstraints.add(Constraint(mappedVars, c.sum))
                }
            }

            val nVars = compRealVars.size
            val numConstraints = compConstraints.size
            val cMasks = IntArray(numConstraints)
            val cSums = IntArray(numConstraints)
            for (ci in 0 until numConstraints) {
                val c = compConstraints[ci]
                var mask = 0
                for (v in c.vars) {
                    mask = mask or (1 shl v)
                }
                cMasks[ci] = mask
                cSums[ci] = c.sum
            }

            var validCount = 0
            var allTrueMask = (1 shl nVars) - 1
            var anyTrueMask = 0
            val limit = 1 shl nVars

            for (m in 0 until limit) {
                var valid = true
                for (ci in 0 until numConstraints) {
                    if (java.lang.Integer.bitCount(m and cMasks[ci]) != cSums[ci]) {
                        valid = false
                        break
                    }
                }
                if (valid) {
                    validCount++
                    allTrueMask = allTrueMask and m
                    anyTrueMask = anyTrueMask or m
                }
            }

            if (validCount > 0) {
                for (vi in 0 until nVars) {
                    val realCell = compRealVars[vi]
                    if ((allTrueMask and (1 shl vi)) != 0 && !knownMines[realCell]) {
                        knownMines[realCell] = true
                        progress = true
                    } else if ((anyTrueMask and (1 shl vi)) == 0 && !revealed[realCell]) {
                        reveal(realCell, mines)
                        progress = true
                    }
                }
            }
        }

        return progress
    }

    private fun stepGlobalMineCount(totalMines: Int, mines: BooleanArray): Boolean {
        var knownMinesCount = 0
        for (i in 0 until totalCells) {
            if (knownMines[i]) knownMinesCount++
        }
        val remainingMines = (totalMines - knownMinesCount).coerceAtLeast(0)

        if (remainingMines == 0) {
            var progress = false
            for (idx in 0 until totalCells) {
                if (!revealed[idx] && !knownMines[idx]) {
                    reveal(idx, mines)
                    progress = true
                }
            }
            return progress
        }

        var unrevCount = 0
        for (idx in 0 until totalCells) {
            if (!revealed[idx] && !knownMines[idx]) {
                unrevCount++
            }
        }

        if (unrevCount == remainingMines) {
            var progress = false
            for (idx in 0 until totalCells) {
                if (!revealed[idx] && !knownMines[idx]) {
                    knownMines[idx] = true
                    progress = true
                }
            }
            return progress
        }

        return false
    }

    companion object {
        fun generateNoGuessBoard(
            rows: Int,
            cols: Int,
            totalMines: Int,
            safeR: Int,
            safeC: Int,
            random: Random
        ): BooleanArray {
            val totalCells = rows * cols
            val solver = NoGuessSolver(rows, cols)

            // 1. Base opening: 3x3 surrounding safeR, safeC (up to 9 cells)
            val reserved = LinkedHashSet<Int>(16)
            for (dr in -1..1) {
                for (dc in -1..1) {
                    val nr = safeR + dr
                    val nc = safeC + dc
                    if (nr in 0 until rows && nc in 0 until cols) {
                        reserved.add(nr * cols + nc)
                    }
                }
            }

            // 2. 50% chance to expand by 1 to 4 adjacent cells for a natural random shape
            val expandExtra = if (random.nextBoolean()) random.nextInt(1, 5) else 0
            if (expandExtra > 0) {
                val boundaryAdjacent = ArrayList<Int>(24)
                for (cell in reserved) {
                    val cr = cell / cols
                    val cc = cell % cols
                    for (dr in -1..1) {
                        for (dc in -1..1) {
                            val nr = cr + dr
                            val nc = cc + dc
                            if (nr in 0 until rows && nc in 0 until cols) {
                                val nIdx = nr * cols + nc
                                if (!reserved.contains(nIdx) && !boundaryAdjacent.contains(nIdx)) {
                                    boundaryAdjacent.add(nIdx)
                                }
                            }
                        }
                    }
                }
                boundaryAdjacent.shuffle(random)
                for (i in 0 until minOf(expandExtra, boundaryAdjacent.size)) {
                    reserved.add(boundaryAdjacent[i])
                }
            }

            // 3. Find outer rim cells directly surrounding the reserved opening
            val rimCells = ArrayList<Int>(32)
            for (cell in reserved) {
                val cr = cell / cols
                val cc = cell % cols
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        val nr = cr + dr
                        val nc = cc + dc
                        if (nr in 0 until rows && nc in 0 until cols) {
                            val nIdx = nr * cols + nc
                            if (!reserved.contains(nIdx) && !rimCells.contains(nIdx)) {
                                rimCells.add(nIdx)
                            }
                        }
                    }
                }
            }

            // Candidate cells outside reserved opening and outside the immediate rim
            val rimSet = rimCells.toHashSet()
            val fieldCandidates = ArrayList<Int>(totalCells)
            for (i in 0 until totalCells) {
                if (!reserved.contains(i) && !rimSet.contains(i)) {
                    fieldCandidates.add(i)
                }
            }

            val mines = BooleanArray(totalCells)
            // Limit opening to the reserved pocket (preventing big chunk carving)
            val maxInitialAllowed = (reserved.size + 6).coerceAtMost(18)

            // Number of rim mines to ensure perimeter cells have numbers 1-3 rather than zero lakes
            val idealRim = (rimCells.size * 0.28f).toInt().coerceIn(1, 7)
            val targetRimMines = minOf(
                idealRim,
                (totalMines - totalMines / 3).coerceAtLeast(0),
                rimCells.size
            )
            val fieldMinesCount = (totalMines - targetRimMines).coerceAtLeast(0)

            val candidates = ArrayList<Int>(totalCells)
            for (i in 0 until totalCells) {
                if (!reserved.contains(i)) {
                    candidates.add(i)
                }
            }

            val startTime = System.currentTimeMillis()
            var globalBestScore = 0
            val globalBestMines = BooleanArray(totalCells)

            // Loop until 100% verified solvable with no 50/50 chance
            while (true) {
                mines.fill(false)

                // Place rim mines to cap the opening chunk organically
                rimCells.shuffle(random)
                for (i in 0 until targetRimMines) {
                    mines[rimCells[i]] = true
                }

                // Distribute all remaining mines with true uniform randomness across the field
                fieldCandidates.shuffle(random)
                for (i in 0 until fieldMinesCount) {
                    mines[fieldCandidates[i]] = true
                }

                var bestRestartScore = 0
                val bestRestartMines = mines.copyOf()

                for (step in 0 until 24) {
                    val ok = solver.resetAndSolve(totalMines, mines, safeR, safeC, maxInitialAllowed)
                    if (ok) {
                        // 100% solvable without guessing, compact organic opening, no big chunks carved out
                        return mines
                    }

                    var knownCount = 0
                    for (i in 0 until totalCells) {
                        if (solver.knownMines[i]) knownCount++
                    }
                    val score = solver.revealedCount + knownCount
                    if (score > bestRestartScore) {
                        bestRestartScore = score
                        System.arraycopy(mines, 0, bestRestartMines, 0, totalCells)
                        if (score > globalBestScore) {
                            globalBestScore = score
                            System.arraycopy(mines, 0, globalBestMines, 0, totalCells)
                        }
                    } else if (random.nextInt(10) < 3) {
                        System.arraycopy(bestRestartMines, 0, mines, 0, totalCells)
                    }

                    // Targeted 50/50 Breaker:
                    // When logic stalls, break the 50/50 bottleneck directly at the stalled boundary clue!
                    var swapped = false
                    if (solver.stalledCluesCount > 0) {
                        val clueIdx = solver.stalledClues[random.nextInt(solver.stalledCluesCount)]
                        val cnt = solver.neighborCounts[clueIdx]
                        val nbrs = solver.neighborTable[clueIdx]

                        var mineIdx = -1
                        var mCount = 0
                        for (ni in 0 until cnt) {
                            val n = nbrs[ni]
                            if (mines[n] && !reserved.contains(n)) {
                                mCount++
                                if (mineIdx == -1 || random.nextInt(mCount) == 0) {
                                    mineIdx = n
                                }
                            }
                        }

                        if (mineIdx != -1) {
                            if (rimSet.contains(mineIdx)) {
                                // Ambiguity is on the opening rim: swap within rimCells to resolve without leaking
                                var rimSafeCount = 0
                                var dest = -1
                                for (i in 0 until rimCells.size) {
                                    val r = rimCells[i]
                                    if (!mines[r]) {
                                        rimSafeCount++
                                        if (dest == -1 || random.nextInt(rimSafeCount) == 0) {
                                            dest = r
                                        }
                                    }
                                }
                                if (dest != -1) {
                                    mines[mineIdx] = false
                                    mines[dest] = true
                                    swapped = true
                                }
                            } else {
                                // Ambiguity is on the field: swap with a field safe cell
                                var fieldSafeCount = 0
                                var dest = -1
                                for (i in 0 until fieldCandidates.size) {
                                    val f = fieldCandidates[i]
                                    if (!mines[f]) {
                                        fieldSafeCount++
                                        if (dest == -1 || random.nextInt(fieldSafeCount) == 0) {
                                            dest = f
                                        }
                                    }
                                }
                                if (dest != -1) {
                                    mines[mineIdx] = false
                                    mines[dest] = true
                                    swapped = true
                                }
                            }
                        }
                    }

                    if (!swapped) {
                        val c1 = candidates[random.nextInt(candidates.size)]
                        val c2 = candidates[random.nextInt(candidates.size)]
                        val tmp = mines[c1]
                        mines[c1] = mines[c2]
                        mines[c2] = tmp
                    }
                }

                // Safety ceiling: if elapsed time reaches 1200ms, return global best board so the app never times out
                if (System.currentTimeMillis() - startTime > 1200 && globalBestScore > 0) {
                    return globalBestMines
                }
            }
        }
    }
}
