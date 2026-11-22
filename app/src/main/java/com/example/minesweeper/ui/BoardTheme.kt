package com.example.minesweeper.ui

import androidx.core.graphics.toColorInt
import com.example.minesweeper.R

/**
 * Defines the colors for all board elements and UI components in a board theme.
 */
enum class BoardTheme(
    val id: String,
    val nameRes: Int,
    val surfaceColor: Int,       // Screen background color
    val toolbarColor: Int,       // Top toolbar background
    val primaryCardColor: Int,   // Stat cards / primary buttons
    val primaryCardText: Int,    // Text color on primary card/buttons
    val secondaryCardColor: Int, // Secondary buttons / card border
    val secondaryCardText: Int,  // Text color on secondary buttons
    val hintCardColor: Int,      // Hint container background
    val hintCardText: Int,       // Hint text color
    val cellHidden: Int,         // Hidden cell color 1
    val cellHiddenAlt: Int,      // Hidden cell color 2
    val cellRevealed: Int,       // Solved/revealed cell color 1
    val cellRevealedAlt: Int,    // Solved/revealed cell color 2
    val mineColor: Int,          // Mine body color
    val mineCoreColor: Int,      // Mine core dot color
    val flagColor: Int,          // Flag fabric color
    val numberColors: IntArray,  // 9-element array for number colors (indices 1..8)
) {
    LILIES(
        id = "lilies",
        nameRes = R.string.theme_lilies,
        surfaceColor = "#388E2A".toColorInt(),
        toolbarColor = "#388E2A".toColorInt(),
        primaryCardColor = "#E1788B".toColorInt(),
        primaryCardText = "#FFFFFF".toColorInt(),
        secondaryCardColor = "#E59EAB".toColorInt(),
        secondaryCardText = "#FFFFFF".toColorInt(),
        hintCardColor = "#7EBF50".toColorInt(),
        hintCardText = "#FFFFFF".toColorInt(),
        cellHidden = "#7EBF50".toColorInt(),
        cellHiddenAlt = "#98CE71".toColorInt(),
        cellRevealed = "#E1788B".toColorInt(),
        cellRevealedAlt = "#E59EAB".toColorInt(),
        mineColor = "#212121".toColorInt(),
        mineCoreColor = "#FFFFFF".toColorInt(),
        flagColor = "#FFFFFF".toColorInt(),
        numberColors = intArrayOf(
            0,
            "#1565C0".toColorInt(), // 1: Deep Royal Blue
            "#1B5E20".toColorInt(), // 2: Forest Green
            "#C62828".toColorInt(), // 3: Crimson Red
            "#4A148C".toColorInt(), // 4: Royal Purple
            "#4E342E".toColorInt(), // 5: Espresso Brown
            "#00695C".toColorInt(), // 6: Dark Teal
            "#212121".toColorInt(), // 7: Charcoal Black
            "#37474F".toColorInt(), // 8: Slate Gray
        ),
    ),
    PLUM_OLIVE(
        id = "plum_olive",
        nameRes = R.string.theme_plum_olive,
        surfaceColor = "#3B391A".toColorInt(),
        toolbarColor = "#3B391A".toColorInt(),
        primaryCardColor = "#603156".toColorInt(),
        primaryCardText = "#FDF8EB".toColorInt(),
        secondaryCardColor = "#7A406D".toColorInt(),
        secondaryCardText = "#FDF8EB".toColorInt(),
        hintCardColor = "#8A863F".toColorInt(),
        hintCardText = "#FDF8EB".toColorInt(),
        cellHidden = "#8A863F".toColorInt(),
        cellHiddenAlt = "#A39F54".toColorInt(),
        cellRevealed = "#603156".toColorInt(),
        cellRevealedAlt = "#7A406D".toColorInt(),
        mineColor = "#231220".toColorInt(),
        mineCoreColor = "#FDF8EB".toColorInt(),
        flagColor = "#F5D061".toColorInt(),
        numberColors = intArrayOf(
            0,
            "#FDF8EB".toColorInt(), // 1: Cream White
            "#C8D678".toColorInt(), // 2: Olive Lime
            "#FFD54F".toColorInt(), // 3: Amber Yellow
            "#80DEEA".toColorInt(), // 4: Cyan
            "#FFAB91".toColorInt(), // 5: Coral
            "#A5D6A7".toColorInt(), // 6: Mint
            "#FFE082".toColorInt(), // 7: Gold
            "#E1BEE7".toColorInt(), // 8: Lavender
        ),
    ),
    COTTON_CANDY(
        id = "cotton_candy",
        nameRes = R.string.theme_cotton_candy,
        surfaceColor = "#5C2E39".toColorInt(),
        toolbarColor = "#5C2E39".toColorInt(),
        primaryCardColor = "#B85A6C".toColorInt(),
        primaryCardText = "#FFFFFF".toColorInt(),
        secondaryCardColor = "#9E4A5B".toColorInt(),
        secondaryCardText = "#FFFFFF".toColorInt(),
        hintCardColor = "#BAE7CA".toColorInt(),
        hintCardText = "#234036".toColorInt(),
        cellHidden = "#BAE7CA".toColorInt(),
        cellHiddenAlt = "#A2D9B5".toColorInt(),
        cellRevealed = "#B85A6C".toColorInt(),
        cellRevealedAlt = "#C5707F".toColorInt(),
        mineColor = "#2E1A21".toColorInt(),
        mineCoreColor = "#BAE7CA".toColorInt(),
        flagColor = "#FFFFFF".toColorInt(),
        numberColors = intArrayOf(
            0,
            "#FFFFFF".toColorInt(), // 1: White
            "#BAE7CA".toColorInt(), // 2: Mint
            "#FFE4A3".toColorInt(), // 3: Light Gold
            "#B3E5EC".toColorInt(), // 4: Light Cyan
            "#FFD9CC".toColorInt(), // 5: Peach
            "#E6D4F7".toColorInt(), // 6: Lavender
            "#FFF3B0".toColorInt(), // 7: Pale Yellow
            "#CEF0FF".toColorInt(), // 8: Pale Blue
        ),
    ),
    SILK_TOUCH(
        id = "silk_touch",
        nameRes = R.string.theme_silk_touch,
        surfaceColor = "#333F55".toColorInt(),
        toolbarColor = "#333F55".toColorInt(),
        primaryCardColor = "#4E739F".toColorInt(),
        primaryCardText = "#FFFFFF".toColorInt(),
        secondaryCardColor = "#7A70AE".toColorInt(),
        secondaryCardText = "#FFFFFF".toColorInt(),
        hintCardColor = "#F7F4EA".toColorInt(),
        hintCardText = "#33303F".toColorInt(),
        cellHidden = "#C0B9DD".toColorInt(),
        cellHiddenAlt = "#B0A8D5".toColorInt(),
        cellRevealed = "#DED9E2".toColorInt(),
        cellRevealedAlt = "#CFC8D9".toColorInt(),
        mineColor = "#2B2A38".toColorInt(),
        mineCoreColor = "#75C9C8".toColorInt(),
        flagColor = "#75C9C8".toColorInt(),
        numberColors = intArrayOf(
            0,
            "#1B3F8B".toColorInt(), // 1: Deep Blue
            "#1F6F5C".toColorInt(), // 2: Pine Green
            "#A63A55".toColorInt(), // 3: Raspberry
            "#5B3E9C".toColorInt(), // 4: Violet
            "#8C4A1F".toColorInt(), // 5: Russet
            "#0F7A79".toColorInt(), // 6: Dark Teal
            "#2F3A56".toColorInt(), // 7: Slate Navy
            "#4A4A57".toColorInt(), // 8: Graphite
        ),
    ),
    LAVENDER_MINT(
        id = "lavender_mint",
        nameRes = R.string.theme_lavender_mint,
        surfaceColor = "#2E3320".toColorInt(),
        toolbarColor = "#2E3320".toColorInt(),
        primaryCardColor = "#8A5E9A".toColorInt(),
        primaryCardText = "#FFFFFF".toColorInt(),
        secondaryCardColor = "#6B7F31".toColorInt(),
        secondaryCardText = "#FFFFFF".toColorInt(),
        hintCardColor = "#A3BC5C".toColorInt(),
        hintCardText = "#23290F".toColorInt(),
        cellHidden = "#88A240".toColorInt(),
        cellHiddenAlt = "#7A9338".toColorInt(),
        cellRevealed = "#C7A9D4".toColorInt(),
        cellRevealedAlt = "#B492C6".toColorInt(),
        mineColor = "#35223C".toColorInt(),
        mineCoreColor = "#88A240".toColorInt(),
        flagColor = "#6E3F7A".toColorInt(),
        numberColors = intArrayOf(
            0,
            "#1D4ED8".toColorInt(), // 1: Blue
            "#2C5C16".toColorInt(), // 2: Olive Green
            "#B3123E".toColorInt(), // 3: Crimson
            "#5B2A83".toColorInt(), // 4: Purple
            "#8A4B12".toColorInt(), // 5: Russet
            "#0F5F63".toColorInt(), // 6: Teal
            "#2A2438".toColorInt(), // 7: Ink
            "#4A4A4A".toColorInt(), // 8: Gray
        ),
    );

    companion object {
        val DEFAULT = LILIES

        fun fromId(id: String?): BoardTheme {
            return entries.firstOrNull { (it.id == id || it.name == id) } ?: DEFAULT
        }
    }
}