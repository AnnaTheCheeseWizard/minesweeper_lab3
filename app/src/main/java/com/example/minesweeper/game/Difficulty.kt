package com.example.minesweeper.game

/** Standard Minesweeper difficulty presets. */
enum class Difficulty(val rows: Int, val cols: Int, val mines: Int) {
    BEGINNER(9, 9, 10),
    INTERMEDIATE(16, 16, 40),
    EXPERT(30, 16, 99),
}
