package com.example.minesweeper.game

/**
 * Returns indices (row * cols + col) of all existing neighbours of [index]
 * on a [rows] x [cols] grid, in ascending order.
 */
fun neighborIndices(index: Int, rows: Int, cols: Int): IntArray {
    val row = index / cols
    val col = index % cols
    val result = IntArray(8)
    var count = 0
    for (dr in -1..1) {
        for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = row + dr
            val nc = col + dc
            if (nr in 0 until rows && nc in 0 until cols) {
                result[count++] = nr * cols + nc
            }
        }
    }
    return result.copyOf(count)
}
