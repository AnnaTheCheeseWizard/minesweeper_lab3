package com.example.minesweeper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

/** Information about a flag color option. */
data class FlagColorInfo(
    val nameRes: Int,
    val color: Int,
)

/**
 * Grid adapter for the flag picker (3x3 grid layout).
 */
class FlagGridAdapter(
    private val context: Context,
    private val flagColors: List<FlagColorInfo>,
    private var selectedColor: Int,
    private val textColor: Int,
    private val surfaceColor: Int,
) : BaseAdapter() {

    fun setSelected(color: Int) {
        selectedColor = color
        notifyDataSetChanged()
    }

    override fun getCount(): Int = flagColors.size

    override fun getItem(position: Int): FlagColorInfo = flagColors[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val cell = (convertView as? FlagGridCellView) ?: FlagGridCellView(context)
        val info = flagColors[position]
        val isSelected = info.color == selectedColor
        cell.bind(info, isSelected, textColor, surfaceColor)
        return cell
    }
}

/**
 * Single grid cell: a larger square box containing the flag illustration, with name underneath.
 */
class FlagGridCellView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val squareBox = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
        }
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private val flagDrawingView = FlagDrawingView(context)

    private val nameView = TextView(context).apply {
        textSize = 13f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        setTypeface(null, Typeface.BOLD)
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(4), dp(4), dp(4))

        val boxSize = dp(84)
        squareBox.addView(
            flagDrawingView,
            LayoutParams(dp(56), dp(56)),
        )

        addView(squareBox, LayoutParams(boxSize, boxSize))
        addView(
            nameView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            },
        )
    }

    fun bind(info: FlagColorInfo, isSelected: Boolean, textColor: Int, surfaceColor: Int) {
        nameView.text = context.getString(info.nameRes)
        nameView.setTextColor(textColor)

        flagDrawingView.setFlagColor(info.color)

        val bg = squareBox.background as? GradientDrawable
        if (isSelected) {
            bg?.setColor(textColor)
            nameView.setTextColor(surfaceColor)
        } else {
            bg?.setColor(0x1AFFFFFF)
            nameView.setTextColor(textColor)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/**
 * Draws a clear, beautiful picture/illustration of a flag (pole + fabric) inside the square.
 * Prepared for future flag shapes and styles.
 */
class FlagDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var currentColor: Int = 0xFFEC4899.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val polePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.DKGRAY
    }
    private val path = Path()

    fun setFlagColor(color: Int) {
        currentColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        // Draw pole
        val poleWidth = w * 0.1f
        val poleX = cx - w * 0.25f
        polePaint.color = Color.parseColor("#71717A")
        canvas.drawRect(poleX, cy - h * 0.38f, poleX + poleWidth, cy + h * 0.38f, polePaint)

        // Draw pole top finial ball
        paint.color = Color.parseColor("#A1A1AA")
        canvas.drawCircle(poleX + poleWidth / 2f, cy - h * 0.38f, w * 0.08f, paint)

        // Draw flag fabric (classic triangular banner shape, ready for future shapes)
        paint.color = currentColor
        val flagTop = cy - h * 0.32f
        val flagBottom = cy + h * 0.12f
        val flagRight = cx + w * 0.38f
        val flagMidY = (flagTop + flagBottom) / 2f

        path.reset()
        path.moveTo(poleX + poleWidth, flagTop)
        path.lineTo(flagRight, flagMidY)
        path.lineTo(poleX + poleWidth, flagBottom)
        path.close()

        canvas.drawPath(path, paint)

        // Draw fabric border outline
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.parseColor("#3F3F46")
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }
}