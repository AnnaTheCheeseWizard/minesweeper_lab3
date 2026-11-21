package com.example.minesweeper.solver

import com.example.minesweeper.game.Board
import com.example.minesweeper.game.BoardSnapshot
import com.example.minesweeper.game.CellState
import com.example.minesweeper.game.Difficulty
import com.example.minesweeper.game.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MinesweeperSolverTest {

    private val solver = MinesweeperSolver()

    @Test
    fun emptyBoardGivesCentreAsFirstMove() {
        val snapshot = BoardSnapshot.parse(
            10,
            "?????????", "?????????", "?????????", "?????????", "?????????",
            "?????????", "?????????", "?????????", "?????????",
        )
        val moves = solver.solve(snapshot)
        assertEquals(1, moves.size)
        assertEquals(Move(4, 4, MoveAction.REVEAL, MoveReason.FIRST_MOVE, moves[0].mineProbability), moves[0])
    }

    @Test
    fun simpleRuleFindsMineWhenNumberEqualsHiddenNeighbours() {
        val snapshot = BoardSnapshot.parse(
            1,
            "1?",
            "11",
        )
        val moves = solver.solve(snapshot)
        assertEquals(1, moves.size)
        assertEquals(0, moves[0].row)
        assertEquals(1, moves[0].col)
        assertEquals(MoveAction.FLAG, moves[0].action)
        assertEquals(MoveReason.SIMPLE_RULE, moves[0].reason)
    }

    @Test
    fun subsetRuleSolvesOneTwoOnePattern() {
        // Hidden A B C above the numbers 1 2 1: A and C are mines, B is safe.
        val snapshot = BoardSnapshot.parse(
            2,
            "???",
            "121",
        )
        val moves = solver.solve(snapshot)
        assertEquals(2, moves.size)
        assertTrue(moves.all { it.action == MoveAction.FLAG && it.reason == MoveReason.SUBSET_RULE })
        assertEquals(setOf(0 to 0, 0 to 2), moves.map { it.row to it.col }.toSet())
    }

    @Test
    fun exactEnumerationRevealsCellBetweenKnownMines() {
        // Same pattern, but the player already flagged both mines.
        val snapshot = BoardSnapshot.parse(
            2,
            "F?F",
            "121",
        )
        val moves = solver.solve(snapshot)
        assertEquals(1, moves.size)
        assertEquals(0, moves[0].row)
        assertEquals(1, moves[0].col)
        assertEquals(MoveAction.REVEAL, moves[0].action)
        assertEquals(MoveReason.EXACT_ENUMERATION, moves[0].reason)
    }

    @Test
    fun globalMineCounterProvesFarCellIsSafe() {
        // The single mine is at (0,1), so the untouched cell (0,2) must be safe.
        val snapshot = BoardSnapshot.parse(1, "1F?")
        val moves = solver.solve(snapshot)
        assertEquals(1, moves.size)
        assertEquals(0, moves[0].row)
        assertEquals(2, moves[0].col)
        assertEquals(MoveAction.REVEAL, moves[0].action)
        assertEquals(MoveReason.EXACT_ENUMERATION, moves[0].reason)
    }

    @Test
    fun guessesWithLowestProbabilityWhenNothingIsCertain() {
        // One mine among eight symmetric neighbours: each has probability 1/8.
        val snapshot = BoardSnapshot.parse(
            1,
            "???",
            "?1?",
            "???",
        )
        val moves = solver.solve(snapshot)
        assertEquals(1, moves.size)
        assertEquals(MoveReason.GUESS, moves[0].reason)
        assertEquals(0.125, moves[0].mineProbability, 1e-9)
    }

    @Test
    fun returnsNothingWhenOnlyKnownFlaggedMinesRemain() {
        val snapshot = BoardSnapshot.parse(1, "11", "1F")
        // Only a flagged cell remains and it is a known mine -> nothing useful to do.
        val moves = solver.solve(snapshot)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun certainMovesAreNeverWrongOnRandomGames() {
        var checkedMoves = 0
        for (seed in 0 until 150) {
            val difficulty = if (seed % 3 == 0) Difficulty.INTERMEDIATE else Difficulty.BEGINNER
            val board = Board(difficulty, Random(seed))
            checkedMoves += playGame(board, verifyCertainMoves = true).second
        }
        assertTrue("solver should have made some certain moves", checkedMoves > 1000)
    }

    @Test
    fun solverWinsMostBeginnerGames() {
        val games = 200
        var wins = 0
        for (seed in 0 until games) {
            if (playGame(Board(Difficulty.BEGINNER, Random(1000 + seed)), false).first) wins++
        }
        assertTrue("won only $wins of $games beginner games", wins >= games * 85 / 100)
    }

    @Test
    fun solverWinsMostIntermediateGames() {
        val games = 60
        var wins = 0
        for (seed in 0 until games) {
            if (playGame(Board(Difficulty.INTERMEDIATE, Random(2000 + seed)), false).first) wins++
        }
        assertTrue("won only $wins of $games intermediate games", wins >= games * 65 / 100)
    }

    /**
     * Lets the solver play a complete game.
     * @return (won, number of certain moves checked)
     */
    private fun playGame(board: Board, verifyCertainMoves: Boolean): Pair<Boolean, Int> {
        var checked = 0
        var iterations = 0
        while (board.status == GameStatus.READY || board.status == GameStatus.PLAYING) {
            iterations++
            check(iterations < 5000) { "solver does not make progress" }
            val moves = solver.solve(board.snapshot())
            if (moves.isEmpty()) break
            for (move in moves) {
                val certain = move.reason != MoveReason.GUESS && move.reason != MoveReason.FIRST_MOVE
                if (verifyCertainMoves && certain) {
                    val isMine = board.isMine(move.row, move.col)
                    assertEquals(
                        "wrong ${move.reason} move $move",
                        move.action == MoveAction.FLAG,
                        isMine,
                    )
                    checked++
                }
                when (move.action) {
                    MoveAction.FLAG -> {
                        if (board.stateAt(move.row, move.col) == CellState.HIDDEN) {
                            board.toggleFlag(move.row, move.col)
                        }
                    }
                    MoveAction.REVEAL -> {
                        if (board.stateAt(move.row, move.col) == CellState.FLAGGED) {
                            board.toggleFlag(move.row, move.col)
                        }
                        board.reveal(move.row, move.col)
                    }
                }
            }
        }
        return (board.status == GameStatus.WON) to checked
    }
}
