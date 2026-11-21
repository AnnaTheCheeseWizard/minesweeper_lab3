package com.example.minesweeper

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.example.minesweeper.game.Board
import com.example.minesweeper.game.Difficulty
import com.example.minesweeper.game.GameStatus

/**
 * Holds the current game so it survives configuration changes
 * (screen rotation, language switch, dark mode toggle).
 */
class GameViewModel : ViewModel() {

    var difficulty: Difficulty = Difficulty.BEGINNER
        private set

    var board: Board = Board(Difficulty.BEGINNER)
        private set

    /** True once the "you won / you lost" dialog has been shown for the current game. */
    var resultShown: Boolean = false

    private var initialized = false
    private var startedAtMs = 0L
    private var finishedAtMs = 0L

    fun initIfNeeded(initialDifficulty: Difficulty) {
        if (initialized) return
        initialized = true
        newGame(initialDifficulty)
    }

    fun newGame(newDifficulty: Difficulty = difficulty) {
        difficulty = newDifficulty
        board = Board(newDifficulty)
        startedAtMs = 0L
        finishedAtMs = 0L
        resultShown = false
    }

    /** Must be called after every change of the board to keep the timer in sync. */
    fun onBoardChanged() {
        val now = SystemClock.elapsedRealtime()
        if (board.status == GameStatus.PLAYING && startedAtMs == 0L) {
            startedAtMs = now
        }
        if (board.isFinished && startedAtMs != 0L && finishedAtMs == 0L) {
            finishedAtMs = now
        }
    }

    fun elapsedSeconds(): Int {
        return when {
            startedAtMs == 0L -> 0
            finishedAtMs != 0L -> ((finishedAtMs - startedAtMs) / 1000).toInt()
            else -> ((SystemClock.elapsedRealtime() - startedAtMs) / 1000).toInt()
        }
    }
}
