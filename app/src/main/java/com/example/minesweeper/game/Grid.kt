package com.example.minesweeper.game

import java.util.concurrent.ConcurrentHashMap

private val tables = ConcurrentHashMap<Long, Array<IntArray>>()

/** table[i] = індекси сусідів клітинки i за зростанням. Масиви не змінювати! */
fun neighborTable(rows: Int, cols: Int): Array<IntArray> =
    tables.getOrPut((rows.toLong() shl 32) or cols.toLong()) { buildTable(rows, cols) }

fun neighborIndices(index: Int, rows: Int, cols: Int): IntArray =
    neighborTable(rows, cols)[index]

private fun buildTable(rows: Int, cols: Int): Array<IntArray> = Array(rows * cols) { index ->
    val row = index / cols
    val col = index % cols
    val result = IntArray(8)
    var count = 0
    for (dr in -1..1) for (dc in -1..1) {
        if (dr == 0 && dc == 0) continue
        val nr = row + dr
        val nc = col + dc
        if (nr in 0 until rows && nc in 0 until cols) result[count++] = nr * cols + nc
    }
    result.copyOf(count)
}