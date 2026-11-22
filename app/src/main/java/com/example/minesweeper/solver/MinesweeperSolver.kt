package com.example.minesweeper.solver

import com.example.minesweeper.game.BoardSnapshot
import com.example.minesweeper.game.neighborIndices
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Minesweeper solver working only on what the player can see ([BoardSnapshot]).
 *
 * It tries progressively stronger (and slower) techniques and returns the moves
 * found by the first one that succeeds:
 *
 *  1. **Simple rule** – a number whose hidden neighbours must be all safe or all mines.
 *  2. **Subset rule** – if the hidden neighbours of number A are a subset of those of B,
 *     the difference is determined by `B - A`.
 *  3. **Exact enumeration (CSP)** – the hidden cells touching numbers (the *frontier*) are split
 *     into independent components; every consistent mine layout of each component is enumerated
 *     by backtracking and combined with the global mine counter to obtain the exact probability
 *     of a mine in every cell. Cells with probability 0 / 1 are certain moves.
 *  4. **Guess** – if nothing is certain, the cell with the lowest mine probability is suggested.
 *
 * Flags set by the player are deliberately ignored (treated as unknown cells) because a flag may be wrong.
 */
class MinesweeperSolver(private val nodeBudget: Int = DEFAULT_NODE_BUDGET) {

    /** Returns the suggested moves for the position; empty if there is nothing to do. */
    fun solve(snapshot: BoardSnapshot): List<Move> {
        val unknownTotal = snapshot.cells.count { it < 0 }
        if (unknownTotal == 0) return emptyList()

        if (snapshot.cells.none { it >= 0 }) {
            val move = firstMove(snapshot)
            Timber.d("Solver: first move at (%d, %d)", move.row, move.col)
            return listOf(move)
        }

        val constraints = buildConstraints(snapshot)

        val simple = simpleRules(snapshot, constraints)
        if (simple.isNotEmpty()) {
            Timber.d("Solver: %d moves by simple rule", simple.size)
            return simple
        }

        val subset = subsetRules(snapshot, constraints)
        if (subset.isNotEmpty()) {
            Timber.d("Solver: %d moves by subset rule", subset.size)
            return subset
        }

        val exact = exactAnalysis(snapshot, constraints, unknownTotal)
        Timber.d("Solver: %d moves by exact analysis (guess=%b)", exact.size, exact.firstOrNull()?.isGuess == true)
        return exact
    }

    // ---------------------------------------------------------------- constraints

    /** "Exactly [mines] of [cells] are mines". [cells] are sorted unknown neighbours of a revealed number. */
    private class Constraint(val cells: IntArray, val mines: Int)

    private fun buildConstraints(snapshot: BoardSnapshot): List<Constraint> {
        val result = ArrayList<Constraint>()
        val seen = HashSet<String>()
        for (i in snapshot.cells.indices) {
            val value = snapshot.cells[i]
            if (value < 0) continue
            val unknown = neighborIndices(i, snapshot.rows, snapshot.cols)
                .filter { snapshot.cells[it] < 0 }
                .sorted()
            if (unknown.isEmpty()) continue
            if (!seen.add("$value:${unknown.joinToString(",")}")) continue
            result.add(Constraint(unknown.toIntArray(), value))
        }
        return result
    }

    private fun firstMove(snapshot: BoardSnapshot): Move {
        val row = snapshot.rows / 2
        val col = snapshot.cols / 2
        val probability = snapshot.totalMines.toDouble() / snapshot.cells.size
        return Move(row, col, MoveAction.REVEAL, MoveReason.FIRST_MOVE, probability)
    }

    private fun buildMoves(
        snapshot: BoardSnapshot,
        found: Map<Int, MoveAction>,
        reason: MoveReason,
    ): List<Move> {
        val moves = ArrayList<Move>()
        for ((index, action) in found) {
            // Flagging an already flagged mine is not a useful move.
            if (action == MoveAction.FLAG && snapshot.cells[index] == BoardSnapshot.FLAGGED) continue
            val probability = if (action == MoveAction.FLAG) 1.0 else 0.0
            moves.add(Move(index / snapshot.cols, index % snapshot.cols, action, reason, probability))
        }
        moves.sortBy { it.row * snapshot.cols + it.col }
        return moves
    }

    // ---------------------------------------------------------------- layer 1: simple rule

    private fun simpleRules(snapshot: BoardSnapshot, constraints: List<Constraint>): List<Move> {
        val found = LinkedHashMap<Int, MoveAction>()
        for (c in constraints) {
            if (c.mines == 0) {
                for (cell in c.cells) found[cell] = MoveAction.REVEAL
            } else if (c.mines == c.cells.size) {
                for (cell in c.cells) found[cell] = MoveAction.FLAG
            }
        }
        return buildMoves(snapshot, found, MoveReason.SIMPLE_RULE)
    }

    // ---------------------------------------------------------------- layer 2: subset rule

    private fun subsetRules(snapshot: BoardSnapshot, constraints: List<Constraint>): List<Move> {
        val found = LinkedHashMap<Int, MoveAction>()
        for (a in constraints) {
            for (b in constraints) {
                if (a.cells.size >= b.cells.size) continue
                if (!isSubset(a.cells, b.cells)) continue
                val diff = difference(b.cells, a.cells)
                val diffMines = b.mines - a.mines
                if (diffMines == 0) {
                    for (cell in diff) found[cell] = MoveAction.REVEAL
                } else if (diffMines == diff.size) {
                    for (cell in diff) found[cell] = MoveAction.FLAG
                }
            }
        }
        return buildMoves(snapshot, found, MoveReason.SUBSET_RULE)
    }

    /** Both arrays must be sorted. */
    private fun isSubset(a: IntArray, b: IntArray): Boolean {
        var j = 0
        for (x in a) {
            while (j < b.size && b[j] < x) j++
            if (j == b.size || b[j] != x) return false
            j++
        }
        return true
    }

    /** Elements of [b] that are not in [a]; both arrays must be sorted. */
    private fun difference(b: IntArray, a: IntArray): List<Int> {
        val result = ArrayList<Int>()
        var j = 0
        for (x in b) {
            while (j < a.size && a[j] < x) j++
            if (j < a.size && a[j] == x) {
                j++
            } else {
                result.add(x)
            }
        }
        return result
    }

    // ---------------------------------------------------------------- layer 3: exact analysis

    /** Result of enumerating one independent group of frontier cells. */
    private class ComponentResult(
        /** Global indices of the component's cells. */
        val cells: IntArray,
        /** counts[k] = number of consistent layouts with exactly k mines. */
        val counts: LongArray,
        /** cellCounts[k][x] = number of layouts with k mines where local cell x is a mine. */
        val cellCounts: Array<LongArray>,
        /** False if the search was aborted because it exceeded the node budget. */
        val exact: Boolean,
    ) {
        val totalLayouts: Long get() = counts.sum()

        fun mineLayouts(x: Int): Long {
            var sum = 0L
            for (k in cellCounts.indices) sum += cellCounts[k][x]
            return sum
        }
    }

    private fun exactAnalysis(
        snapshot: BoardSnapshot,
        constraints: List<Constraint>,
        unknownTotal: Int,
    ): List<Move> {
        val totalMines = snapshot.totalMines

        // Frontier cells = unknown cells adjacent to at least one number.
        val frontierId = HashMap<Int, Int>()
        val frontierCells = ArrayList<Int>()
        for (c in constraints) {
            for (cell in c.cells) {
                if (!frontierId.containsKey(cell)) {
                    frontierId[cell] = frontierCells.size
                    frontierCells.add(cell)
                }
            }
        }

        // Union-find: cells sharing a constraint belong to the same component.
        val parent = IntArray(frontierCells.size) { it }
        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }
        for (c in constraints) {
            val first = find(frontierId.getValue(c.cells[0]))
            for (k in 1 until c.cells.size) {
                val other = find(frontierId.getValue(c.cells[k]))
                if (other != first) parent[other] = first
            }
        }

        val cellsByRoot = LinkedHashMap<Int, MutableList<Int>>()
        for (id in frontierCells.indices) {
            cellsByRoot.getOrPut(find(id)) { ArrayList() }.add(frontierCells[id])
        }
        val constraintsByRoot = HashMap<Int, MutableList<Constraint>>()
        for (c in constraints) {
            constraintsByRoot.getOrPut(find(frontierId.getValue(c.cells[0]))) { ArrayList() }.add(c)
        }

        val components = ArrayList<ComponentResult>()
        for ((root, cells) in cellsByRoot) {
            components.add(enumerateComponent(cells, constraintsByRoot.getValue(root), totalMines))
        }
        Timber.d("Solver: %d constraints, %d frontier cells, %d components", constraints.size, frontierCells.size, components.size)

        if (components.any { it.totalLayouts == 0L && it.exact }) {
            Timber.w("Solver: position is inconsistent (no valid mine layout)")
            return emptyList()
        }
        if (components.any { !it.exact }) {
            return fallbackAnalysis(snapshot, constraints, components, unknownTotal)
        }

        // ---- combine components with the global mine counter (BigInteger: numbers get huge) ----
        val nonFrontier = unknownTotal - frontierCells.size
        val ways = binomialRow(nonFrontier, totalMines) // ways[j] = C(nonFrontier, j)
        fun waysFor(remaining: Int): BigInteger =
            if (remaining < 0 || remaining > totalMines) BigInteger.ZERO else ways[remaining]

        val limit = totalMines + 1
        val polynomials = components.map { comp ->
            List(comp.counts.size) { k -> BigInteger.valueOf(comp.counts[k]) }
        }
        val one = listOf(BigInteger.ONE)
        val prefix = ArrayList<List<BigInteger>>(polynomials.size + 1)
        prefix.add(one)
        for (p in polynomials) prefix.add(convolve(prefix.last(), p, limit))
        val suffix = Array(polynomials.size + 1) { one }
        for (j in polynomials.indices.reversed()) suffix[j] = convolve(polynomials[j], suffix[j + 1], limit)
        val full: List<BigInteger> = prefix.last()

        var total = BigInteger.ZERO
        for (s in full.indices) total += full[s] * waysFor(totalMines - s)
        if (total.signum() == 0) {
            Timber.w("Solver: mine counter contradicts the visible numbers")
            return emptyList()
        }

        val probability = DoubleArray(snapshot.cells.size) { Double.NaN }
        val safeCells = ArrayList<Int>()
        val mineCells = ArrayList<Int>()

        for (i in components.indices) {
            val others = convolve(prefix[i], suffix[i + 1], limit)
            val comp = components[i]
            val weightForK = Array(comp.counts.size) { k ->
                var w = BigInteger.ZERO
                for (s in others.indices) w += others[s] * waysFor(totalMines - k - s)
                w
            }
            for (x in comp.cells.indices) {
                var numerator = BigInteger.ZERO
                for (k in comp.counts.indices) {
                    val count = comp.cellCounts[k][x]
                    if (count != 0L) numerator += BigInteger.valueOf(count) * weightForK[k]
                }
                val cell = comp.cells[x]
                probability[cell] = ratio(numerator, total)
                if (numerator.signum() == 0) {
                    safeCells.add(cell)
                } else if (numerator == total) {
                    mineCells.add(cell)
                }
            }
        }

        if (nonFrontier > 0) {
            var numerator = BigInteger.ZERO
            for (s in full.indices) {
                val remaining = totalMines - s
                numerator += full[s] * waysFor(remaining) * BigInteger.valueOf(remaining.toLong())
            }
            val denominator = total * BigInteger.valueOf(nonFrontier.toLong())
            val p = ratio(numerator, denominator)
            val allSafe = numerator.signum() == 0
            val allMines = numerator == denominator
            for (i in snapshot.cells.indices) {
                if (snapshot.cells[i] < 0 && !frontierId.containsKey(i)) {
                    probability[i] = p
                    if (allSafe) {
                        safeCells.add(i)
                    } else if (allMines) {
                        mineCells.add(i)
                    }
                }
            }
        }

        val found = LinkedHashMap<Int, MoveAction>()
        for (cell in safeCells) found[cell] = MoveAction.REVEAL
        for (cell in mineCells) found[cell] = MoveAction.FLAG
        val certain = buildMoves(snapshot, found, MoveReason.EXACT_ENUMERATION)
        if (certain.isNotEmpty()) return certain

        return pickGuess(snapshot, probability)
    }

    /**
     * Used only if some component is too large to enumerate within the node budget.
     * Cells that are certain inside fully enumerated components are still used;
     * otherwise a heuristic probability is used for the guess.
     */
    private fun fallbackAnalysis(
        snapshot: BoardSnapshot,
        constraints: List<Constraint>,
        components: List<ComponentResult>,
        unknownTotal: Int,
    ): List<Move> {
        val found = LinkedHashMap<Int, MoveAction>()
        for (comp in components) {
            if (!comp.exact) continue
            val total = comp.totalLayouts
            for (x in comp.cells.indices) {
                val mineLayouts = comp.mineLayouts(x)
                if (mineLayouts == 0L) {
                    found[comp.cells[x]] = MoveAction.REVEAL
                } else if (mineLayouts == total) {
                    found[comp.cells[x]] = MoveAction.FLAG
                }
            }
        }
        val certain = buildMoves(snapshot, found, MoveReason.EXACT_ENUMERATION)
        if (certain.isNotEmpty()) return certain

        val density = (snapshot.totalMines.toDouble() / unknownTotal).coerceIn(0.0, 1.0)
        val frontierProbability = DoubleArray(snapshot.cells.size) { -1.0 }
        for (c in constraints) {
            val p = c.mines.toDouble() / c.cells.size
            for (cell in c.cells) frontierProbability[cell] = max(frontierProbability[cell], p)
        }
        val probability = DoubleArray(snapshot.cells.size) { Double.NaN }
        for (i in snapshot.cells.indices) {
            if (snapshot.cells[i] < 0) {
                probability[i] = if (frontierProbability[i] >= 0.0) frontierProbability[i] else density
            }
        }
        for (comp in components) {
            if (!comp.exact) continue
            val total = comp.totalLayouts.toDouble()
            for (x in comp.cells.indices) {
                probability[comp.cells[x]] = comp.mineLayouts(x) / total
            }
        }
        return pickGuess(snapshot, probability)
    }

    /**
     * Backtracking enumeration of all mine layouts of one component.
     * Cells are assigned in BFS order so constraints become fully determined early and prune the search.
     */
    private fun enumerateComponent(
        cells: List<Int>,
        constraints: List<Constraint>,
        maxMines: Int,
    ): ComponentResult {
        val n = cells.size
        val local = HashMap<Int, Int>()
        for ((i, cell) in cells.withIndex()) local[cell] = i

        val constraintCells = Array(constraints.size) { ci ->
            IntArray(constraints[ci].cells.size) { j -> local.getValue(constraints[ci].cells[j]) }
        }
        val need = IntArray(constraints.size) { constraints[it].mines }

        val perCell = Array(n) { ArrayList<Int>() }
        for (ci in constraintCells.indices) {
            for (x in constraintCells[ci]) perCell[x].add(ci)
        }
        val cellConstraints = Array(n) { perCell[it].toIntArray() }

        val order = IntArray(n)
        var count = 0
        val visited = BooleanArray(n)
        val queue = ArrayDeque<Int>()
        visited[0] = true
        queue.addLast(0)
        while (queue.isNotEmpty()) {
            val x = queue.removeFirst()
            order[count++] = x
            for (ci in cellConstraints[x]) {
                for (y in constraintCells[ci]) {
                    if (!visited[y]) {
                        visited[y] = true
                        queue.addLast(y)
                    }
                }
            }
        }
        check(count == n) { "Component must be connected" }

        val search = Search(n, constraintCells, need, cellConstraints, order, maxMines, nodeBudget)
        search.run()
        if (search.aborted) {
            Timber.w("Solver: component of %d cells exceeded the node budget", n)
        }
        return ComponentResult(cells.toIntArray(), search.counts, search.cellCounts, !search.aborted)
    }

    private class Search(
        private val n: Int,
        private val constraintCells: Array<IntArray>,
        need: IntArray,
        private val cellConstraints: Array<IntArray>,
        private val order: IntArray,
        private val maxMines: Int,
        private val budget: Int,
    ) {
        private val remaining = need.copyOf()
        private val unassigned = IntArray(constraintCells.size) { constraintCells[it].size }
        private val assignment = IntArray(n)
        private var nodes = 0

        val counts = LongArray(n + 1)
        val cellCounts = Array(n + 1) { LongArray(n) }
        var aborted = false
            private set

        fun run() {
            recurse(0, 0)
        }

        private fun recurse(depth: Int, mines: Int) {
            if (aborted) return
            if (depth == n) {
                counts[mines]++
                val row = cellCounts[mines]
                for (i in 0 until n) {
                    if (assignment[i] == 1) row[i]++
                }
                return
            }
            nodes++
            if (nodes > budget) {
                aborted = true
                return
            }
            val cell = order[depth]
            for (value in 0..1) {
                if (mines + value > maxMines) continue
                if (apply(cell, value)) {
                    assignment[cell] = value
                    recurse(depth + 1, mines + value)
                }
                revert(cell, value)
            }
        }

        /** Assigns [value] to [cell]; returns false if some constraint became unsatisfiable. */
        private fun apply(cell: Int, value: Int): Boolean {
            var ok = true
            for (ci in cellConstraints[cell]) {
                unassigned[ci]--
                remaining[ci] -= value
                if (remaining[ci] < 0 || remaining[ci] > unassigned[ci]) ok = false
            }
            return ok
        }

        private fun revert(cell: Int, value: Int) {
            for (ci in cellConstraints[cell]) {
                unassigned[ci]++
                remaining[ci] += value
            }
        }
    }

    // ---------------------------------------------------------------- guessing and maths helpers

    /**
     * Picks the hidden (not flagged) cell with the lowest mine probability. Ties are broken by the
     * chance that the cell and all its neighbours are free of mines, which favours corners and edges
     * and tends to open larger areas.
     */
    private fun pickGuess(snapshot: BoardSnapshot, probability: DoubleArray): List<Move> {
        var best = -1
        var bestProbability = 2.0
        var bestZeroChance = -1.0
        for (i in snapshot.cells.indices) {
            if (snapshot.cells[i] != BoardSnapshot.HIDDEN) continue
            val p = probability[i]
            if (p.isNaN()) continue
            val unknownNeighbors = neighborIndices(i, snapshot.rows, snapshot.cols)
                .count { snapshot.cells[it] < 0 }
            val zeroChance = (1.0 - p).pow(unknownNeighbors + 1)
            val better = p < bestProbability - EPSILON ||
                (abs(p - bestProbability) <= EPSILON && zeroChance > bestZeroChance)
            if (better) {
                best = i
                bestProbability = p
                bestZeroChance = zeroChance
            }
        }
        if (best < 0) return emptyList()
        return listOf(
            Move(best / snapshot.cols, best % snapshot.cols, MoveAction.REVEAL, MoveReason.GUESS, bestProbability)
        )
    }

    /** Row of binomial coefficients C(n, 0.maxK). */
    private fun binomialRow(n: Int, maxK: Int): Array<BigInteger> {
        val row = Array<BigInteger>(maxK + 1) { BigInteger.ZERO }
        row[0] = BigInteger.ONE
        for (k in 0 until maxK) {
            row[k + 1] = row[k] * BigInteger.valueOf((n - k).toLong()) / BigInteger.valueOf((k + 1).toLong())
        }
        return row
    }

    /** Polynomial product of [a] and [b], truncated to [limit] coefficients. */
    private fun convolve(a: List<BigInteger>, b: List<BigInteger>, limit: Int): List<BigInteger> {
        val size = minOf(a.size + b.size - 1, limit)
        val result = Array<BigInteger>(size) { BigInteger.ZERO }
        for (i in a.indices) {
            if (a[i].signum() == 0) continue
            for (j in b.indices) {
                val k = i + j
                if (k >= size) break
                result[k] = result[k] + a[i] * b[j]
            }
        }
        return result.toList()
    }

    private fun ratio(numerator: BigInteger, denominator: BigInteger): Double {
        if (numerator.signum() == 0) return 0.0
        return BigDecimal(numerator).divide(BigDecimal(denominator), MathContext.DECIMAL64).toDouble()
    }

    companion object {
        const val DEFAULT_NODE_BUDGET = 300_000
        private const val EPSILON = 1e-12
    }
}
