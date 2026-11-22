package com.example.minesweeper.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.minesweeper.R
import com.example.minesweeper.game.Board
import com.example.minesweeper.game.CellState
import com.example.minesweeper.game.GameStatus
import com.example.minesweeper.solver.Move
import com.example.minesweeper.solver.MoveAction
import com.example.minesweeper.solver.MoveReason
import kotlin.math.min

/**
 * Custom view that draws the minesweeper board on a Canvas with square cells and customizable board themes.
 */
class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onCellReveal(row: Int, col: Int)
        fun onCellFlag(row: Int, col: Int)
    }

    var listener: Listener? = null

    private var board: Board? = null
    private var hints: List<Move> = emptyList()
    var currentTheme: BoardTheme = BoardTheme.DEFAULT
        private set

    /** Custom flag color override (-1 means use theme default). */
    var customFlagColor: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    private var cellSize = 0f
    private var originX = 0f
    private var originY = 0f

    private val rect = RectF()
    private val path = Path()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val colorExploded = color(R.color.cell_exploded)
    private val colorHintSafe = color(R.color.hint_safe)
    private val colorHintMine = color(R.color.hint_mine)
    private val colorHintGuess = color(R.color.hint_guess)

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                performClick()
                withCellAt(e.x, e.y) { row, col ->
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    listener?.onCellFlag(row, col)
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                performClick()
                withCellAt(e.x, e.y) { row, col -> listener?.onCellReveal(row, col) }
                return true
            }
        },
    )

    fun bind(board: Board) {
        this.board = board
        this.hints = emptyList()
        invalidate()
    }

    fun setTheme(theme: BoardTheme) {
        this.currentTheme = theme
        invalidate()
    }

    fun setHints(moves: List<Move>) {
        hints = moves
        invalidate()
    }

    fun refresh() = invalidate()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val board = board ?: return
        canvas.drawColor(currentTheme.surfaceColor)

        cellSize = min(width.toFloat() / board.cols, height.toFloat() / board.rows)
        originX = (width - cellSize * board.cols) / 2f
        originY = (height - cellSize * board.rows) / 2f
        val gap = maxOf(0.5f, cellSize * 0.04f)
        textPaint.textSize = cellSize * 0.62f

        val finished = board.isFinished
        val lost = board.status == GameStatus.LOST

        for (row in 0 until board.rows) {
            for (col in 0 until board.cols) {
                rect.set(
                    originX + col * cellSize + gap,
                    originY + row * cellSize + gap,
                    originX + (col + 1) * cellSize - gap,
                    originY + (row + 1) * cellSize - gap,
                )
                val state = board.stateAt(row, col)
                val isMine = finished && board.isMine(row, col)
                val exploded = lost && board.index(row, col) == board.explodedIndex
                val isEven = (row + col) % 2 == 0
                val hiddenBg = if (isEven) currentTheme.cellHidden else currentTheme.cellHiddenAlt
                val revealedBg = if (isEven) currentTheme.cellRevealed else currentTheme.cellRevealedAlt

                when {
                    state == CellState.REVEALED && isMine -> {
                        drawCellBackground(canvas, if (exploded) colorExploded else revealedBg)
                        drawMine(canvas)
                    }
                    state == CellState.REVEALED -> {
                        drawCellBackground(canvas, revealedBg)
                        val number = board.adjacentMines(row, col)
                        if (number > 0) drawNumber(canvas, number)
                    }
                    state == CellState.FLAGGED && lost && !isMine -> {
                        drawCellBackground(canvas, revealedBg)
                        drawFlag(canvas)
                        drawCross(canvas)
                    }
                    state == CellState.FLAGGED -> {
                        drawCellBackground(canvas, hiddenBg)
                        drawFlag(canvas)
                    }
                    lost && isMine -> {
                        drawCellBackground(canvas, revealedBg)
                        drawMine(canvas)
                    }
                    else -> drawCellBackground(canvas, hiddenBg)
                }
            }
        }

        drawHints(canvas, board)
    }

    private fun drawCellBackground(canvas: Canvas, color: Int) {
        val cornerRadius = maxOf(3f, cellSize * 0.15f)
        fillPaint.color = color
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
    }

    private fun drawNumber(canvas: Canvas, number: Int) {
        if (number in 1..8) {
            textPaint.color = currentTheme.numberColors[number]
        }
        val metrics = textPaint.fontMetrics
        val y = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(number.toString(), rect.centerX(), y, textPaint)
    }

    private fun drawMine(canvas: Canvas) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = cellSize * 0.22f
        val coreRadius = cellSize * 0.08f
        val spike = cellSize * 0.34f
        strokePaint.color = currentTheme.mineColor
        strokePaint.strokeWidth = maxOf(1.5f, cellSize * 0.07f)
        canvas.drawLine(cx - spike, cy, cx + spike, cy, strokePaint)
        canvas.drawLine(cx, cy - spike, cx, cy + spike, strokePaint)
        val diagonal = spike * 0.72f
        canvas.drawLine(cx - diagonal, cy - diagonal, cx + diagonal, cy + diagonal, strokePaint)
        canvas.drawLine(cx - diagonal, cy + diagonal, cx + diagonal, cy - diagonal, strokePaint)
        fillPaint.color = currentTheme.mineColor
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.color = currentTheme.mineCoreColor
        canvas.drawCircle(cx, cy, coreRadius, fillPaint)
    }

    private fun drawFlag(canvas: Canvas) {
        val cx = rect.centerX()
        val top = rect.top + cellSize * 0.2f
        val bottom = rect.bottom - cellSize * 0.2f
        val poleX = cx - cellSize * 0.04f
        strokePaint.color = currentTheme.mineColor
        strokePaint.strokeWidth = maxOf(1.5f, cellSize * 0.07f)
        canvas.drawLine(poleX, top, poleX, bottom, strokePaint)
        canvas.drawLine(cx - cellSize * 0.2f, bottom, cx + cellSize * 0.2f, bottom, strokePaint)
        path.reset()
        path.moveTo(poleX, top)
        path.lineTo(poleX + cellSize * 0.32f, top + cellSize * 0.15f)
        path.lineTo(poleX, top + cellSize * 0.3f)
        path.close()
        fillPaint.color = if (customFlagColor != -1) customFlagColor else currentTheme.flagColor
        canvas.drawPath(path, fillPaint)
    }

    private fun drawCross(canvas: Canvas) {
        strokePaint.color = colorHintMine
        strokePaint.strokeWidth = maxOf(2f, cellSize * 0.09f)
        val m = cellSize * 0.18f
        canvas.drawLine(rect.left + m, rect.top + m, rect.right - m, rect.bottom - m, strokePaint)
        canvas.drawLine(rect.left + m, rect.bottom - m, rect.right - m, rect.top + m, strokePaint)
    }

    private fun drawHints(canvas: Canvas, board: Board) {
        if (hints.isEmpty()) return
        strokePaint.strokeWidth = maxOf(2.5f, cellSize * 0.10f)
        val cornerRadius = maxOf(3f, cellSize * 0.15f)
        for (move in hints) {
            strokePaint.color = when {
                move.reason == MoveReason.GUESS || move.reason == MoveReason.FIRST_MOVE -> colorHintGuess
                move.action == MoveAction.REVEAL -> colorHintSafe
                else -> colorHintMine
            }
            val inset = strokePaint.strokeWidth / 2f
            rect.set(
                originX + move.col * cellSize + inset,
                originY + move.row * cellSize + inset,
                originX + (move.col + 1) * cellSize - inset,
                originY + (move.row + 1) * cellSize - inset,
            )
            if (move.row in 0 until board.rows && move.col in 0 until board.cols) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
            }
        }
    }

    private inline fun withCellAt(x: Float, y: Float, action: (Int, Int) -> Unit) {
        val board = board ?: return
        if (cellSize <= 0f) return
        val dx = x - originX
        val dy = y - originY
        if (dx < 0f || dy < 0f) return
        val col = (dx / cellSize).toInt()
        val row = (dy / cellSize).toInt()
        if (row in 0 until board.rows && col in 0 until board.cols) action(row, col)
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)
}