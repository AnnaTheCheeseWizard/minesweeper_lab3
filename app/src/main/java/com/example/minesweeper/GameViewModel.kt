package com.example.minesweeper

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.content.edit
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

    /** True if solver hints, solver steps, or auto-solve were used during the current game. */
    var isAssisted: Boolean = false

    /** Currently selected flag color (persisted across themes). */
    var flagColor: Int = -1
        private set

    private var initialized = false
    private var startedAtMs = 0L
    private var finishedAtMs = 0L
    private var pausedAtMs = 0L

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
        pausedAtMs = 0L
        resultShown = false
        isAssisted = false
    }

    fun pauseTimer() {
        if (startedAtMs != 0L && finishedAtMs == 0L && (pausedAtMs == 0L)) {
            pausedAtMs = SystemClock.elapsedRealtime()
        }
    }

    fun resumeTimer() {
        if (pausedAtMs != 0L) {
            startedAtMs += SystemClock.elapsedRealtime() - pausedAtMs
            pausedAtMs = 0L
        }
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
        val end = when {
            startedAtMs == 0L -> return 0
            finishedAtMs != 0L -> finishedAtMs
            pausedAtMs != 0L -> pausedAtMs
            else -> SystemClock.elapsedRealtime()
        }
        return ((end - startedAtMs) / 1000).toInt()
    }

    /** Load saved flag color preference. Returns -1 if none set. */
    fun loadFlagColor(prefs: SharedPreferences): Int {
        flagColor = prefs.getInt(KEY_FLAG_COLOR, -1)
        return flagColor
    }

    /** Save flag color preference. */
    fun saveFlagColor(prefs: SharedPreferences, color: Int) {
        flagColor = color
        prefs.edit { putInt(KEY_FLAG_COLOR, color) }
    }

    private companion object {
        const val KEY_FLAG_COLOR = "flag_color"
    }
}