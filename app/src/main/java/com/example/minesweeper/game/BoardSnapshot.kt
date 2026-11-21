package com.example.minesweeper.game

/**
 * What a human player can see on the board. The solver works only with this
 * class, so it can never "cheat" by looking at the hidden mines.
 *
 * Cell values: [HIDDEN], [FLAGGED] or the number 0..8 of a revealed cell.
 */
class BoardSnapshot(
    val rows: Int,
    val cols: Int,
    val totalMines: Int,
    val cells: IntArray,
) {
    init {
        require(cells.size == rows * cols) { "cells.size must be rows * cols" }
    }

    companion object {
        const val HIDDEN = -1
        const val FLAGGED = -2

        /**
         * Builds a snapshot from text rows: '?' hidden, 'F' flagged,
         * digit = revealed number. Handy for tests.
         */
        fun parse(totalMines: Int, vararg lines: String): BoardSnapshot {
            val cols = lines[0].length
            val values = ArrayList<Int>()
            for (line in lines) {
                require(line.length == cols) { "All rows must have the same length" }
                for (ch in line) {
                    values.add(
                        when (ch) {
                            '?' -> HIDDEN
                            'F' -> FLAGGED
                            else -> ch.digitToInt()
                        }
                    )
                }
            }
            return BoardSnapshot(lines.size, cols, totalMines, values.toIntArray())
        }
    }
}
