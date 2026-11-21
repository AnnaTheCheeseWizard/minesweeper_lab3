package com.example.minesweeper.game

/** Minesweeper difficulty presets optimized with square cells for portrait mobile screens. */
enum class Difficulty(val rows: Int, val cols: Int, val mines: Int) {
    BEGINNER(14, 6, 10),
    INTERMEDIATE(20, 9, 30),
    EXPERT(26, 12, 60),
}