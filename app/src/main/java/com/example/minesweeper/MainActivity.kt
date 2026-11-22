package com.example.minesweeper

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.minesweeper.game.CellState
import com.example.minesweeper.game.Difficulty
import com.example.minesweeper.game.GameStatus
import com.example.minesweeper.solver.MinesweeperSolver
import com.example.minesweeper.solver.Move
import com.example.minesweeper.solver.MoveAction
import com.example.minesweeper.solver.MoveReason
import com.example.minesweeper.ui.BoardTheme
import com.example.minesweeper.ui.BoardView
import com.example.minesweeper.ui.FlagColorInfo
import com.example.minesweeper.ui.FlagGridAdapter
import com.example.minesweeper.ui.ThemeChoiceAdapter
import com.example.minesweeper.util.LanguageManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import timber.log.Timber
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), BoardView.Listener {

    private val viewModel: GameViewModel by viewModels()
    private val solver = MinesweeperSolver()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var rootView: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var boardView: BoardView
    private lateinit var minesText: TextView
    private lateinit var timerText: TextView
    private lateinit var bestTimeText: TextView
    private lateinit var difficultyButton: Button
    private lateinit var hintText: TextView
    private lateinit var autoButton: Button

    private lateinit var bestTimeCard: MaterialCardView
    private lateinit var minesCard: MaterialCardView
    private lateinit var timerCard: MaterialCardView
    private lateinit var boardCard: MaterialCardView
    private lateinit var hintCard: MaterialCardView
    private lateinit var restartButton: Button
    private lateinit var hintButton: Button
    private lateinit var stepButton: Button

    private var autoRunning = false
    private var shownSeconds = -1

    private val timerRunnable = object : Runnable {
        override fun run() {
            val seconds = viewModel.elapsedSeconds()
            if (seconds != shownSeconds) {
                shownSeconds = seconds
                timerText.text = getString(R.string.label_time, seconds)
            }
            if (viewModel.board.status == GameStatus.PLAYING) {
                handler.postDelayed(this, TIMER_INTERVAL_MS)
            }
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

        rootView = findViewById(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        boardView = findViewById(R.id.boardView)
        minesText = findViewById(R.id.minesText)
        timerText = findViewById(R.id.timerText)
        bestTimeText = findViewById(R.id.bestTimeText)
        difficultyButton = findViewById(R.id.difficultyButton)
        hintText = findViewById(R.id.hintText)
        autoButton = findViewById(R.id.autoButton)

        bestTimeCard = findViewById(R.id.bestTimeCard)
        minesCard = findViewById(R.id.minesCard)
        timerCard = findViewById(R.id.timerCard)
        boardCard = findViewById(R.id.boardCard)
        hintCard = findViewById(R.id.hintCard)
        restartButton = findViewById(R.id.restartButton)
        hintButton = findViewById(R.id.hintButton)
        stepButton = findViewById(R.id.stepButton)

        boardView.listener = this
        difficultyButton.setOnClickListener { showDifficultyDialog() }
        restartButton.setOnClickListener { startNewGame(viewModel.difficulty) }
        hintButton.setOnClickListener { showHint() }
        stepButton.setOnClickListener {
            stopAuto()
            performSolverStep()
        }
        autoButton.setOnClickListener { if (autoRunning) stopAuto() else startAuto() }

        val activeTheme = loadTheme()
        applyBoardTheme(activeTheme)

        // Load and apply saved flag color
        val savedFlagColor = viewModel.loadFlagColor(getSharedPreferences(PREFS, MODE_PRIVATE))
        boardView.customFlagColor = savedFlagColor

        viewModel.initIfNeeded(loadDifficulty())
        boardView.bind(viewModel.board)
        updateStatusTexts()
    }

    override fun onStart() {
        super.onStart()
        viewModel.resumeTimer()
        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)
    }

    override fun onStop() {
        super.onStop()
        viewModel.pauseTimer()
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
            R.id.action_theme -> {
                showThemeDialog()
                true
            }
            R.id.action_flags -> {
                showFlagColorDialog()
                true
            }
            R.id.action_language -> {
                showLanguageDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showThemeDialog() {
        val themes = BoardTheme.entries
        val names = themes.map { getString(it.nameRes) }
        val activeTheme = loadTheme()
        val adapter = ThemeChoiceAdapter(themes, names, activeTheme.primaryCardText)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_theme)
            .setSingleChoiceItems(adapter, themes.indexOf(activeTheme)) { dialogInterface, which ->
                val selected = themes[which]
                saveTheme(selected)
                applyBoardTheme(selected)
                dialogInterface.dismiss()
            }
            .setNegativeButton(R.string.dialog_close, null)
            .create()

        dialog.show()
        styleDialog(dialog, activeTheme)
    }

    private fun applyBoardTheme(theme: BoardTheme) {
        rootView.setBackgroundColor(theme.surfaceColor)
        toolbar.setBackgroundColor(theme.toolbarColor)
        toolbar.setTitleTextColor(theme.primaryCardText)

        bestTimeCard.setCardBackgroundColor(theme.primaryCardColor)
        bestTimeCard.strokeColor = theme.secondaryCardColor
        bestTimeText.setTextColor(theme.primaryCardText)

        minesCard.setCardBackgroundColor(theme.primaryCardColor)
        minesCard.strokeColor = theme.secondaryCardColor
        minesText.setTextColor(theme.primaryCardText)

        timerCard.setCardBackgroundColor(theme.primaryCardColor)
        timerCard.strokeColor = theme.secondaryCardColor
        timerText.setTextColor(theme.primaryCardText)

        (difficultyButton as? MaterialButton)?.backgroundTintList = ColorStateList.valueOf(theme.primaryCardColor)
        difficultyButton.setTextColor(theme.primaryCardText)

        (restartButton as? MaterialButton)?.backgroundTintList = ColorStateList.valueOf(theme.secondaryCardColor)
        restartButton.setTextColor(theme.secondaryCardText)

        (hintButton as? MaterialButton)?.backgroundTintList = ColorStateList.valueOf(theme.secondaryCardColor)
        hintButton.setTextColor(theme.secondaryCardText)

        (stepButton as? MaterialButton)?.backgroundTintList = ColorStateList.valueOf(theme.secondaryCardColor)
        stepButton.setTextColor(theme.secondaryCardText)

        (autoButton as? MaterialButton)?.backgroundTintList = ColorStateList.valueOf(theme.primaryCardColor)
        autoButton.setTextColor(theme.primaryCardText)

        hintCard.setCardBackgroundColor(theme.hintCardColor)
        hintCard.strokeColor = theme.secondaryCardColor
        hintText.setTextColor(theme.hintCardText)

        boardCard.setCardBackgroundColor(theme.surfaceColor)
        boardCard.strokeColor = theme.hintCardColor

        boardView.setTheme(theme)
        applySystemBarIcons(theme)
    }

    private fun showFlagColorDialog() {
        val activeTheme = loadTheme()
        val flagColors = listOf(
            FlagColorInfo(R.string.flag_color_blue, "#2196F3".toColorInt()),
            FlagColorInfo(R.string.flag_color_red, "#F44336".toColorInt()),
            FlagColorInfo(R.string.flag_color_green, "#4CAF50".toColorInt()),
            FlagColorInfo(R.string.flag_color_yellow, "#FFEB3B".toColorInt()),
            FlagColorInfo(R.string.flag_color_pink, "#E91E63".toColorInt()),
            FlagColorInfo(R.string.flag_color_white, "#FFFFFF".toColorInt()),
            FlagColorInfo(R.string.flag_color_black, "#212121".toColorInt()),
        )
        val currentFlagColor = viewModel.flagColor

        val dialogView = layoutInflater.inflate(R.layout.dialog_flag_picker, null)
        val gridView = dialogView.findViewById<android.widget.GridView>(R.id.flagGridView)
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        titleView.setText(R.string.menu_flags)
        titleView.setTextColor(activeTheme.primaryCardText)

        val adapter = FlagGridAdapter(this, flagColors, currentFlagColor, activeTheme.primaryCardText, activeTheme.surfaceColor)
        gridView.adapter = adapter

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setNegativeButton(R.string.dialog_close, null)
            .create()

        gridView.setOnItemClickListener { _, _, position, _ ->
            val selected = flagColors[position]
            viewModel.saveFlagColor(getSharedPreferences(PREFS, MODE_PRIVATE), selected.color)
            boardView.customFlagColor = selected.color
            updateStatusTexts()
            dialog.dismiss()
        }

        dialog.show()
        styleDialog(dialog, activeTheme)
    }

    /** Keeps status/navigation bar icons readable on both light and dark themes. */
    private fun applySystemBarIcons(theme: BoardTheme) {
        val lightBackground = ColorUtils.calculateLuminance(theme.surfaceColor) > 0.5
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
    }

    private fun showDifficultyDialog() {
        val values = Difficulty.entries.toTypedArray()
        val labels = values.map { difficultyLabel(it) }.toTypedArray()
        val activeTheme = loadTheme()
        val adapter = createThemedSingleChoiceAdapter(labels, activeTheme)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_difficulty)
            .setSingleChoiceItems(adapter, values.indexOf(viewModel.difficulty)) { dialogInterface, which ->
                saveDifficulty(values[which])
                startNewGame(values[which])
                dialogInterface.dismiss()
            }
            .setNegativeButton(R.string.dialog_close, null)
            .create()

        dialog.show()
        styleDialog(dialog, activeTheme)
    }

    private fun showLanguageDialog() {
        val labels = LanguageManager.options.map { getString(it.labelRes) }.toTypedArray()
        val activeTheme = loadTheme()
        val adapter = createThemedSingleChoiceAdapter(labels, activeTheme)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_language)
            .setSingleChoiceItems(adapter, LanguageManager.currentIndex()) { dialogInterface, which ->
                dialogInterface.dismiss()
                LanguageManager.select(which)
            }
            .setNegativeButton(R.string.dialog_close, null)
            .create()

        dialog.show()
        styleDialog(dialog, activeTheme)
    }

    private fun createThemedSingleChoiceAdapter(labels: Array<String>, theme: BoardTheme): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                if (view is TextView) {
                    view.setTextColor(theme.primaryCardText)
                    if (view is CheckedTextView) {
                        view.checkMarkTintList = ColorStateList.valueOf(theme.secondaryCardColor)
                    }
                }
                return view
            }
        }
    }

    private fun styleDialog(dialog: AlertDialog, theme: BoardTheme) {
        val drawable = GradientDrawable().apply {
            setColor(theme.surfaceColor)
            setStroke(dpToPx(2), theme.secondaryCardColor)
            cornerRadius = dpToPx(16).toFloat()
        }
        dialog.window?.setBackgroundDrawable(drawable)

        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(theme.primaryCardText)
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(theme.primaryCardText)

        val positiveBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        if (positiveBtn != null) {
            positiveBtn.setTextColor(theme.primaryCardText)
            (positiveBtn as? MaterialButton)?.backgroundTintList = ColorStateList.valueOf(theme.primaryCardColor)
        }

        val negativeBtn = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        if (negativeBtn != null) {
            negativeBtn.setTextColor(theme.secondaryCardText)
            (negativeBtn as? MaterialButton)?.backgroundTintList = ColorStateList.valueOf(theme.secondaryCardColor)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun difficultyLabel(difficulty: Difficulty): String {
        val name = difficultyShortName(difficulty)
        val mines = resources.getQuantityString(R.plurals.mines_count, difficulty.mines, difficulty.mines)
        return getString(R.string.difficulty_item, name, difficulty.cols, difficulty.rows, mines)
    }

    private fun difficultyShortName(difficulty: Difficulty): String {
        return when (difficulty) {
            Difficulty.BEGINNER -> getString(R.string.difficulty_beginner)
            Difficulty.INTERMEDIATE -> getString(R.string.difficulty_intermediate)
            Difficulty.EXPERT -> getString(R.string.difficulty_expert)
        }
    }

    // ---------------------------------------------------------------- touch input

    override fun onCellReveal(row: Int, col: Int) {
        stopAuto()
        clearHints()
        val board = viewModel.board
        val changed = if (board.stateAt(row, col) == CellState.REVEALED) {
            board.chord(row, col)
        } else {
            board.reveal(row, col)
        }
        Timber.d("Reveal/chord at (%d, %d), changed=%b", row, col, changed)
        afterBoardChanged()
    }

    override fun onCellFlag(row: Int, col: Int) {
        stopAuto()
        clearHints()
        val changed = viewModel.board.toggleFlag(row, col)
        Timber.d("Flag toggled at (%d, %d), changed=%b", row, col, changed)
        afterBoardChanged()
    }

    // ---------------------------------------------------------------- solver integration

    private fun showHint() {
        stopAuto()
        viewModel.isAssisted = true
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
        viewModel.isAssisted = true
        val board = viewModel.board
        if (board.isFinished) return false

        val moves = solver.solve(board.snapshot())
        boardView.setHints(emptyList())
        if (moves.isEmpty()) {
            hintText.setText(R.string.hint_none)
            return false
        }

        for ((row, col) in moves.filter { it.action == MoveAction.FLAG }) {
            if (board.stateAt(row, col) == CellState.HIDDEN) {
                board.toggleFlag(row, col)
            }
        }
        for ((row, col) in moves.filter { it.action == MoveAction.REVEAL }) {
            if (board.stateAt(row, col) == CellState.FLAGGED) {
                // The player's flag was wrong: the solver proved the cell is safe.
                board.toggleFlag(row, col)
            }
            board.reveal(row, col)
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
        viewModel.isAssisted = true
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
        if (viewModel.board.status == GameStatus.PLAYING) {
            handler.removeCallbacks(timerRunnable)
            handler.post(timerRunnable)
        }
        if (!keepHint) hintText.setText(R.string.hint_default)
        boardView.refresh()
        updateStatusTexts()
        if (viewModel.board.isFinished && !viewModel.resultShown) {
            viewModel.resultShown = true
            stopAuto()
            val won = viewModel.board.status == GameStatus.WON
            var isNewRecord = false
            if (won && !viewModel.isAssisted) {
                isNewRecord = saveBestTimeIfNeeded(viewModel.difficulty, viewModel.elapsedSeconds())
                updateStatusTexts()
            }
            showResultDialog(isNewRecord)
        }
    }

    private fun updateStatusTexts() {
        val board = viewModel.board
        val difficulty = viewModel.difficulty
        minesText.text = getString(R.string.label_mines, board.minesLeft)
        timerText.text = getString(R.string.label_time, viewModel.elapsedSeconds())

        val bestTime = getBestTime(difficulty)
        val bestTimeString = if (bestTime >= 0) {
            resources.getQuantityString(R.plurals.seconds_count, bestTime, bestTime)
        } else {
            getString(R.string.label_best_none)
        }
        bestTimeText.text = getString(R.string.label_best, bestTimeString)
        difficultyButton.text = getString(R.string.difficulty_button_format, difficultyShortName(difficulty))
    }

    private fun showResultDialog(isNewRecord: Boolean = false) {
        val won = viewModel.board.status == GameStatus.WON
        val seconds = viewModel.elapsedSeconds()
        val time = resources.getQuantityString(R.plurals.seconds_count, seconds, seconds)
        val message = if (isNewRecord) {
            getString(R.string.result_new_record, time)
        } else {
            getString(R.string.result_message, time)
        }
        val activeTheme = loadTheme()
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (won) R.string.result_won_title else R.string.result_lost_title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_new_game) { _, _ -> startNewGame(viewModel.difficulty) }
            .setNegativeButton(R.string.dialog_close, null)
            .create()

        dialog.show()
        styleDialog(dialog, activeTheme)
    }

    // ---------------------------------------------------------------- persistence

    private fun loadTheme(): BoardTheme {
        val id = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_THEME, null)
        return BoardTheme.fromId(id)
    }

    private fun saveTheme(theme: BoardTheme) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit {
                putString(KEY_THEME, theme.id)
            }
    }

    private fun loadDifficulty(): Difficulty {
        val name = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_DIFFICULTY, null)
        return Difficulty.entries.firstOrNull { it.name == name } ?: Difficulty.BEGINNER
    }

    private fun saveDifficulty(difficulty: Difficulty) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit {
                putString(KEY_DIFFICULTY, difficulty.name)
            }
    }

    private fun getBestTime(difficulty: Difficulty): Int {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
            .getInt(KEY_BEST_TIME_PREFIX + difficulty.name, -1)
    }

    private fun saveBestTimeIfNeeded(difficulty: Difficulty, seconds: Int): Boolean {
        val currentBest = getBestTime(difficulty)
        if (currentBest == -1 || seconds < currentBest) {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit {
                    putInt(KEY_BEST_TIME_PREFIX + difficulty.name, seconds)
                }
            return true
        }
        return false
    }

    private companion object {
        const val PREFS = "minesweeper"
        const val KEY_THEME = "theme"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_BEST_TIME_PREFIX = "best_time_"
        const val TIMER_INTERVAL_MS = 250L
        const val AUTO_DELAY_MS = 500L
    }
}