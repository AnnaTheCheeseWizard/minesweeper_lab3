package com.example.minesweeper.solver

enum class MoveAction { REVEAL, FLAG }

/** Why the solver suggests a move. Used by the UI to explain hints. */
enum class MoveReason {
    /** Nothing is revealed yet; any cell is as good as another, so the centre is taken. */
    FIRST_MOVE,

    /** A single number already determines its neighbours (N = 0 or N = hidden neighbours). */
    SIMPLE_RULE,

    /** Comparison of two overlapping numbers: one's hidden cells are a subset of the other's. */
    SUBSET_RULE,

    /** Exhaustive enumeration of all consistent mine layouts on the frontier. */
    EXACT_ENUMERATION,

    /** No safe move exists: the cell with the lowest probability of containing a mine. */
    GUESS,
}

/**
 * A single suggested action. [mineProbability] is 0.0 for safe reveals, 1.0 for
 * certain mines and a value in between for [MoveReason.GUESS] moves.
 */
data class Move(
    val row: Int,
    val col: Int,
    val action: MoveAction,
    val reason: MoveReason,
    val mineProbability: Double,
) {
    val isGuess: Boolean get() = reason == MoveReason.GUESS
}
