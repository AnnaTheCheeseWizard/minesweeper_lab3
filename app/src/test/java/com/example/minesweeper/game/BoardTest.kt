package com.example.minesweeper.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BoardTest {

    @Test
    fun firstRevealIsAlwaysSafeAndOpensZoneAroundIt() {
        for (seed in 0 until 200) {
            val board = Board(9, 9, 10, Random(seed))
            board.reveal(4, 4)
            assertEquals(GameStatus.PLAYING, board.status)
            assertEquals(0, board.adjacentMines(4, 4))
            for (r in 3..5) for (c in 3..5) {
                assertFalse("seed=$seed ($r,$c)", board.isMine(r, c))
            }
        }
    }

    @Test
    fun boardContainsExactlyRequestedNumberOfMines() {
        val board = Board(Difficulty.EXPERT, Random(1))
        board.reveal(0, 0)
        var mines = 0
        for (r in 0 until board.rows) for (c in 0 until board.cols) {
            if (board.isMine(r, c)) mines++
        }
        assertEquals(99, mines)
    }

    @Test
    fun floodFillRevealsWholeEmptyAreaAndWinsTheGame() {
        val board = Board.fromLayout(
            "*...",
            "....",
            "....",
        )
        assertTrue(board.reveal(2, 3))
        assertEquals(GameStatus.WON, board.status)
        assertEquals(CellState.REVEALED, board.stateAt(1, 1))
        assertEquals(1, board.adjacentMines(1, 1))
    }

    @Test
    fun revealingMineLosesTheGame() {
        val board = Board.fromLayout("*.")
        assertTrue(board.reveal(0, 0))
        assertEquals(GameStatus.LOST, board.status)
        assertEquals(0, board.explodedIndex)
        assertFalse("no moves after the game is over", board.reveal(0, 1))
    }

    @Test
    fun flagsAreCountedAndProtectCellsFromReveal() {
        val board = Board.fromLayout(
            "*..",
            "...",
            "..*",
        )
        assertEquals(2, board.minesLeft)
        assertTrue(board.toggleFlag(0, 0))
        assertEquals(1, board.minesLeft)
        assertFalse("flagged cell can't be revealed", board.reveal(0, 0))
        assertEquals(CellState.FLAGGED, board.stateAt(0, 0))
        assertTrue(board.toggleFlag(0, 0))
        assertEquals(2, board.minesLeft)
        assertEquals(CellState.HIDDEN, board.stateAt(0, 0))
    }

    @Test
    fun cannotFlagRevealedCell() {
        val board = Board.fromLayout(
            "*..",
            "...",
            "...",
        )
        board.reveal(1, 1)
        assertFalse(board.toggleFlag(1, 1))
    }

    @Test
    fun chordRevealsNeighboursWhenFlagsMatchNumber() {
        val board = Board.fromLayout(
            "*..",
            "...",
            "...",
        )
        board.reveal(1, 1) // shows "1"
        board.toggleFlag(0, 0)
        assertTrue(board.chord(1, 1))
        assertEquals(GameStatus.WON, board.status)
    }

    @Test
    fun chordDoesNothingWhenFlagCountDiffers() {
        val board = Board.fromLayout(
            "*..",
            "...",
            "...",
        )
        board.reveal(1, 1)
        assertFalse(board.chord(1, 1))
        assertEquals(CellState.HIDDEN, board.stateAt(2, 2))
    }

    @Test
    fun winningFlagsAllMines() {
        val board = Board.fromLayout(
            "*..",
            "...",
            "..*",
        )
        board.reveal(0, 2)
        board.reveal(2, 0)
        board.reveal(0, 1)
        board.reveal(1, 0)
        board.reveal(1, 1)
        board.reveal(1, 2)
        board.reveal(2, 1)
        assertEquals(GameStatus.WON, board.status)
        assertEquals(CellState.FLAGGED, board.stateAt(0, 0))
        assertEquals(CellState.FLAGGED, board.stateAt(2, 2))
        assertEquals(0, board.minesLeft)
    }

    @Test
    fun snapshotHidesMineLocations() {
        val board = Board.fromLayout(
            "*..",
            "...",
            "...",
        )
        board.reveal(1, 1)
        board.toggleFlag(0, 0)
        val snapshot = board.snapshot()
        assertEquals(1, snapshot.cells[board.index(1, 1)])
        assertEquals(BoardSnapshot.FLAGGED, snapshot.cells[0])
        assertEquals(BoardSnapshot.HIDDEN, snapshot.cells[board.index(2, 2)])
    }
}
