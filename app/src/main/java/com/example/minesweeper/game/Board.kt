package com.example.minesweeper.game

import timber.log.Timber
import kotlin.random.Random

enum class CellState { HIDDEN, FLAGGED, REVEALED }

enum class GameStatus { READY, PLAYING, WON, LOST }

/**
 * Minesweeper game model. Pure Kotlin (no Android classes) so it can be unit-tested on the JVM.
 *
 * Mines are placed lazily on the first reveal, so the first click (and, when
 * possible, all its neighbours) is always safe.
 */
class Board(
    val rows: Int,
    val cols: Int,
    val mineCount: Int,
    private val random: Random = Random.Default,
) {
    constructor(difficulty: Difficulty, random: Random = Random.Default) :
        this(difficulty.rows, difficulty.cols, difficulty.mines, random)

    init {
        require(rows > 0 && cols > 0) { "Board must not be empty" }
        require(mineCount in 1 until rows * cols) { "Invalid mine count: $mineCount" }
    }

    private val size = rows * cols
    private val neighbors = neighborTable(rows, cols)
    private val mines = BooleanArray(size)
    private val adjacent = IntArray(size)
    private val states = Array(size) { CellState.HIDDEN }
    private var minesPlaced = false
    private var revealedCount = 0

    var flagCount: Int = 0
        private set

    var status: GameStatus = GameStatus.READY
        private set

    /** Index of the mine that ended the game, or -1. */
    var explodedIndex: Int = -1
        private set

    val minesLeft: Int get() = mineCount - flagCount

    val isFinished: Boolean get() = status == GameStatus.WON || status == GameStatus.LOST

    fun index(row: Int, col: Int): Int = row * cols + col

    fun stateAt(row: Int, col: Int): CellState = states[index(row, col)]

    /** Number of mines around a cell. Meaningful for revealed cells. */
    fun adjacentMines(row: Int, col: Int): Int = adjacent[index(row, col)]

    /** Whether the cell hides a mine. The UI must only use this after the game is finished. */
    fun isMine(row: Int, col: Int): Boolean = mines[index(row, col)]

    /** Reveals a cell. Returns true if the board changed. */
    fun reveal(row: Int, col: Int): Boolean {
        checkBounds(row, col)
        if (isFinished) return false
        val start = index(row, col)
        if (states[start] != CellState.HIDDEN) return false

        if (!minesPlaced) {
            placeMines(start)
            status = GameStatus.PLAYING
            Timber.i("Game started, first reveal at (%d, %d)", row, col)
        }

        if (mines[start]) {
            states[start] = CellState.REVEALED
            explodedIndex = start
            status = GameStatus.LOST
            Timber.i("Mine hit at (%d, %d), game lost", row, col)
            return true
        }

        floodReveal(start)
        if (revealedCount == size - mineCount) {
            win()
        }
        return true
    }

    /** Toggles a flag on a hidden cell. Returns true if the board changed. */
    fun toggleFlag(row: Int, col: Int): Boolean {
        checkBounds(row, col)
        if (isFinished) return false
        val i = index(row, col)
        return when (states[i]) {
            CellState.HIDDEN -> {
                states[i] = CellState.FLAGGED
                flagCount++
                true
            }
            CellState.FLAGGED -> {
                states[i] = CellState.HIDDEN
                flagCount--
                true
            }
            CellState.REVEALED -> false
        }
    }

    /**
     * "Chording": if a revealed number has exactly that many flags around it,
     * reveals all remaining hidden neighbours. Returns true if the board changed.
     */
    fun chord(row: Int, col: Int): Boolean {
        checkBounds(row, col)
        if (status != GameStatus.PLAYING) return false
        val i = index(row, col)
        if (states[i] != CellState.REVEALED || adjacent[i] == 0) return false

        val around = neighbors[i]
        val flags = around.count { states[it] == CellState.FLAGGED }
        if (flags != adjacent[i]) return false

        var changed = false
        for (n in around) {
            if (states[n] == CellState.HIDDEN && reveal(n / cols, n % cols)) {
                changed = true
            }
        }
        return changed
    }

    /** Snapshot of everything a player can see; this is the solver's only input. */
    fun snapshot(): BoardSnapshot {
        val cells = IntArray(size) { i ->
            when (states[i]) {
                CellState.HIDDEN -> BoardSnapshot.HIDDEN
                CellState.FLAGGED -> BoardSnapshot.FLAGGED
                CellState.REVEALED -> adjacent[i]
            }
        }
        return BoardSnapshot(rows, cols, mineCount, cells)
    }

    private fun floodReveal(start: Int) {
        val stack = IntArray(size)
        var top = 0
        states[start] = CellState.REVEALED
        revealedCount++
        stack[top++] = start
        while (top > 0) {
            val current = stack[--top]
            if (adjacent[current] != 0) continue
            for (n in neighbors[current]) {
                if (states[n] == CellState.HIDDEN) {
                    states[n] = CellState.REVEALED
                    revealedCount++
                    stack[top++] = n
                }
            }
        }
    }

    private fun win() {
        status = GameStatus.WON
        for (i in 0 until size) {
            if (mines[i] && states[i] == CellState.HIDDEN) {
                states[i] = CellState.FLAGGED
                flagCount++
            }
        }
        Timber.i("Game won")
    }

    private fun placeMines(firstIndex: Int) {
        val excluded = HashSet<Int>()
        excluded.add(firstIndex)
        for (n in neighbors[firstIndex]) excluded.add(n)
        // On tiny boards there may be no room for a full safe zone.
        if (size - excluded.size < mineCount) {
            excluded.clear()
            excluded.add(firstIndex)
        }
        val candidates = (0 until size).filter { it !in excluded }.shuffled(random)
        for (i in 0 until mineCount) mines[candidates[i]] = true
        computeAdjacency()
        minesPlaced = true
    }

    private fun computeAdjacency() {
        for (i in 0 until size) {
            var count = 0
            for (n in neighbors[i]) if (mines[n]) count++
            adjacent[i] = count
        }
    }

    /** Test helper: places mines explicitly and starts the game. */
    internal fun loadMines(mineIndices: Collection<Int>) {
        for (i in mineIndices) mines[i] = true
        computeAdjacency()
        minesPlaced = true
        status = GameStatus.PLAYING
    }

    private fun checkBounds(row: Int, col: Int) {
        require(row in 0 until rows && col in 0 until cols) { "Cell ($row, $col) is out of bounds" }
    }

    companion object {
        /** Test helper: builds a board from text rows where '*' is a mine. */
        internal fun fromLayout(vararg lines: String): Board {
            val rows = lines.size
            val cols = lines[0].length
            val mineIndices = ArrayList<Int>()
            for ((r, line) in lines.withIndex()) {
                require(line.length == cols) { "All rows must have the same length" }
                for ((c, ch) in line.withIndex()) {
                    if (ch == '*') mineIndices.add(r * cols + c)
                }
            }
            val board = Board(rows, cols, mineIndices.size)
            board.loadMines(mineIndices)
            return board
        }
    }
}
