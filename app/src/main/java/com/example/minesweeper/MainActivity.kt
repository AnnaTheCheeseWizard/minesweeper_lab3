package com.example.minesweeper

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.minesweeper.game.CellState
import com.example.minesweeper.game.Difficulty
import com.example.minesweeper.game.GameStatus
import com.example.minesweeper.solver.Move
import com.example.minesweeper.solver.MoveAction
import com.example.minesweeper.solver.MoveReason
import com.example.minesweeper.solver.MinesweeperSolver
import com.example.minesweeper.ui.BoardView
import com.example.minesweeper.util.LanguageManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import timber.log.Timber
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), BoardView.Listener {

    private val viewModel: GameViewModel by viewModels()
    private val solver = MinesweeperSolver()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var boardView: BoardView
    private lateinit var minesText: TextView
    private lateinit var timerText: TextView
    private lateinit var hintText: TextView
    private lateinit var autoButton: Button

    private var autoRunning = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            updateStatusTexts()
            handler.postDelayed(this, TIMER_INTERVAL_MS)
        }
    }

    private val autoRunnable = object : Runnable {
        override fun run() {
            if (!autoRunning) return
            val progressed = performSolverStep()
            if (progressed && !viewModel.board.isFinished) {
                handler.postDelayed(this, AUTO_DELAY_MS)
            } else {
                stopAuto()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate (recreated=%b)", savedInstanceState != null)
        setContentView(R.layout.activity_main)

        val root = findViewById<android.view.View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        boardView = findViewById(R.id.boardView)
        minesText = findViewById(R.id.minesText)
        timerText = findViewById(R.id.timerText)
        hintText = findViewById(R.id.hintText)
        autoButton = findViewById(R.id.autoButton)

        boardView.listener = this
        findViewById<Button>(R.id.restartButton).setOnClickListener { startNewGame(viewModel.difficulty) }
        findViewById<Button>(R.id.hintButton).setOnClickListener { showHint() }
        findViewById<Button>(R.id.stepButton).setOnClickListener {
            stopAuto()
            performSolverStep()
        }
        autoButton.setOnClickListener { if (autoRunning) stopAuto() else startAuto() }

        viewModel.initIfNeeded(loadDifficulty())
        boardView.bind(viewModel.board)
        updateStatusTexts()
    }

    override fun onStart() {
        super.onStart()
        handler.post(timerRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(timerRunnable)
        stopAuto()
    }

    // ---------------------------------------------------------------- menu

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_game -> {
                startNewGame(viewModel.difficulty)
                true
            }
            R.id.action_difficulty -> {
                showDifficultyDialog()
                true
            }
            R.id.action_language -> {
                showLanguageDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDifficultyDialog() {
        val values = Difficulty.values()
        val labels = values.map { difficultyLabel(it) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_difficulty)
            .setSingleChoiceItems(labels, values.indexOf(viewModel.difficulty)) { dialog, which ->
                saveDifficulty(values[which])
                startNewGame(values[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showLanguageDialog() {
        val labels = LanguageManager.options.map { getString(it.labelRes) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_language)
            .setSingleChoiceItems(labels, LanguageManager.currentIndex()) { dialog, which ->
                dialog.dismiss()
                // AppCompat recreates the activity; the ViewModel keeps the game alive.
                LanguageManager.select(which)
            }
            .show()
    }

    private fun difficultyLabel(difficulty: Difficulty): String {
        val name = when (difficulty) {
            Difficulty.BEGINNER -> getString(R.string.difficulty_beginner)
            Difficulty.INTERMEDIATE -> getString(R.string.difficulty_intermediate)
            Difficulty.EXPERT -> getString(R.string.difficulty_expert)
        }
        val mines = resources.getQuantityString(R.plurals.mines_count, difficulty.mines, difficulty.mines)
        return getString(R.string.difficulty_item, name, difficulty.cols, difficulty.rows, mines)
    }

    // ---------------------------------------------------------------- touch input

    override fun onCellTap(row: Int, col: Int) {
        stopAuto()
        clearHints()
        val board = viewModel.board
        val changed = if (board.stateAt(row, col) == CellState.REVEALED) {
            board.chord(row, col)
        } else {
            board.reveal(row, col)
        }
        Timber.d("Tap at (%d, %d), changed=%b", row, col, changed)
        afterBoardChanged()
    }

    override fun onCellLongPress(row: Int, col: Int) {
        stopAuto()
        clearHints()
        val changed = viewModel.board.toggleFlag(row, col)
        Timber.d("Long press at (%d, %d), flag toggled=%b", row, col, changed)
        afterBoardChanged()
    }

    // ---------------------------------------------------------------- solver integration

    private fun showHint() {
        stopAuto()
        val board = viewModel.board
        if (board.isFinished) return
        val moves = solver.solve(board.snapshot())
        boardView.setHints(moves)
        hintText.text = describeHint(moves)
    }

    /**
     * Applies one batch of solver moves to the board.
     * @return true if the solver found something to do
     */
    private fun performSolverStep(): Boolean {
        val board = viewModel.board
        if (board.isFinished) return false

        val moves = solver.solve(board.snapshot())
        boardView.setHints(emptyList())
        if (moves.isEmpty()) {
            hintText.setText(R.string.hint_none)
            return false
        }

        for (move in moves.filter { it.action == MoveAction.FLAG }) {
            if (board.stateAt(move.row, move.col) == CellState.HIDDEN) {
                board.toggleFlag(move.row, move.col)
            }
        }
        for (move in moves.filter { it.action == MoveAction.REVEAL }) {
            if (board.stateAt(move.row, move.col) == CellState.FLAGGED) {
                // The player's flag was wrong: the solver proved the cell is safe.
                board.toggleFlag(move.row, move.col)
            }
            board.reveal(move.row, move.col)
        }
        Timber.i("Solver applied %d moves (first reason: %s)", moves.size, moves[0].reason)

        val first = moves[0]
        hintText.text = if (first.isGuess) {
            getString(
                R.string.solver_guess,
                first.row + 1,
                first.col + 1,
                (first.mineProbability * 100).roundToInt(),
            )
        } else {
            resources.getQuantityString(R.plurals.solver_applied, moves.size, moves.size) +
                " (" + reasonText(first.reason) + ")"
        }
        afterBoardChanged(keepHint = true)
        return true
    }

    private fun describeHint(moves: List<Move>): String {
        if (moves.isEmpty()) return getString(R.string.hint_none)
        val first = moves[0]
        if (first.isGuess) {
            return getString(
                R.string.hint_guess,
                first.row + 1,
                first.col + 1,
                (first.mineProbability * 100).roundToInt(),
            )
        }
        val count = resources.getQuantityString(R.plurals.hint_moves_found, moves.size, moves.size)
        return getString(R.string.hint_certain_format, count, reasonText(first.reason))
    }

    private fun reasonText(reason: MoveReason): String = getString(
        when (reason) {
            MoveReason.FIRST_MOVE -> R.string.reason_first_move
            MoveReason.SIMPLE_RULE -> R.string.reason_simple
            MoveReason.SUBSET_RULE -> R.string.reason_subset
            MoveReason.EXACT_ENUMERATION -> R.string.reason_exact
            MoveReason.GUESS -> R.string.reason_exact // never shown: guesses use their own message
        }
    )

    private fun startAuto() {
        if (viewModel.board.isFinished) return
        autoRunning = true
        autoButton.setText(R.string.btn_stop)
        Timber.i("Auto-solve started")
        handler.post(autoRunnable)
    }

    private fun stopAuto() {
        if (!autoRunning) return
        autoRunning = false
        handler.removeCallbacks(autoRunnable)
        autoButton.setText(R.string.btn_auto)
        Timber.i("Auto-solve stopped")
    }

    // ---------------------------------------------------------------- game flow

    private fun startNewGame(difficulty: Difficulty) {
        stopAuto()
        viewModel.newGame(difficulty)
        Timber.i("New game: %s", difficulty)
        boardView.bind(viewModel.board)
        hintText.setText(R.string.hint_default)
        updateStatusTexts()
    }

    private fun clearHints() {
        boardView.setHints(emptyList())
    }

    private fun afterBoardChanged(keepHint: Boolean = false) {
        viewModel.onBoardChanged()
        if (!keepHint) hintText.setText(R.string.hint_default)
        boardView.refresh()
        updateStatusTexts()
        if (viewModel.board.isFinished && !viewModel.resultShown) {
            viewModel.resultShown = true
            stopAuto()
            showResultDialog()
        }
    }

    private fun updateStatusTexts() {
        val board = viewModel.board
        minesText.text = getString(R.string.label_mines, board.minesLeft)
        timerText.text = getString(R.string.label_time, viewModel.elapsedSeconds())
    }

    private fun showResultDialog() {
        val won = viewModel.board.status == GameStatus.WON
        val seconds = viewModel.elapsedSeconds()
        val time = resources.getQuantityString(R.plurals.seconds_count, seconds, seconds)
        MaterialAlertDialogBuilder(this)
            .setTitle(if (won) R.string.result_won_title else R.string.result_lost_title)
            .setMessage(getString(R.string.result_message, time))
            .setPositiveButton(R.string.dialog_new_game) { _, _ -> startNewGame(viewModel.difficulty) }
            .setNegativeButton(R.string.dialog_close, null)
            .show()
    }

    // ---------------------------------------------------------------- persistence

    private fun loadDifficulty(): Difficulty {
        val name = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DIFFICULTY, null)
        return Difficulty.values().firstOrNull { it.name == name } ?: Difficulty.BEGINNER
    }

    private fun saveDifficulty(difficulty: Difficulty) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DIFFICULTY, difficulty.name)
            .apply()
    }

    private companion object {
        const val PREFS = "minesweeper"
        const val KEY_DIFFICULTY = "difficulty"
        const val TIMER_INTERVAL_MS = 500L
        const val AUTO_DELAY_MS = 500L
    }
}
