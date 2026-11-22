package com.example.minesweeper.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Checkable
import android.widget.LinearLayout
import android.widget.TextView

/**
 * List adapter for the theme picker. Every row shows a small palette preview
 * (the most characteristic colors of the theme) next to the theme name.
 */
class ThemeChoiceAdapter(
    private val themes: List<BoardTheme>,
    private val names: List<String>,
    private val textColor: Int,
) : BaseAdapter() {

    override fun getCount(): Int = themes.size

    override fun getItem(position: Int): BoardTheme = themes[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = (convertView as? ThemeRowView) ?: ThemeRowView(parent.context)
        row.bind(themes[position], names[position], textColor)
        return row
    }
}

/**
 * Single list row: a row of rounded color swatches, the theme name and a check mark
 * that becomes visible when the row is the selected one.
 */
class ThemeRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs), Checkable {

    private val swatchRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val nameView = TextView(context).apply {
        textSize = 16f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private val checkView = TextView(context).apply {
        text = "\u2713"
        textSize = 18f
        visibility = INVISIBLE
    }

    private var checkedState = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(12))

        addView(swatchRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(
            nameView,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) },
        )
        addView(
            checkView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            },
        )
    }

    fun bind(theme: BoardTheme, name: String, textColor: Int) {
        nameView.text = name
        nameView.setTextColor(textColor)
        checkView.setTextColor(theme.secondaryCardColor)

        swatchRow.removeAllViews()
        val size = dp(16)
        val gap = dp(3)
        theme.swatchColors().forEach { color ->
            val swatch = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(1), SWATCH_STROKE)
                }
                contentDescription = null
            }
            swatchRow.addView(
                swatch,
                LayoutParams(size, size).apply { marginEnd = gap },
            )
        }
    }

    override fun setChecked(checked: Boolean) {
        checkedState = checked
        checkView.visibility = if (checked) VISIBLE else INVISIBLE
    }

    override fun isChecked(): Boolean = checkedState

    override fun toggle() {
        this.isChecked = !checkedState
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SWATCH_STROKE = 0x40FFFFFF
    }
}

/** The colors that best describe a theme, used for the palette preview. */
private fun BoardTheme.swatchColors(): List<Int> = listOf(
    cellHidden,
    cellRevealed,
    hintCardColor,
    primaryCardColor,
    secondaryCardColor,
    flagColor,
)