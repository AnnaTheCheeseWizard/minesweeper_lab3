package com.example.minesweeper.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.minesweeper.R
import timber.log.Timber

/**
 * Per-app language switching (i18n) on top of AppCompat's `setApplicationLocales`.
 * AppCompat stores the choice, recreates the activities and, on Android 13+,
 * synchronises it with the system "App languages" setting (see res/xml/locales_config.xml).
 */
object LanguageManager {

    /** [tag] is a BCP-47 language tag, or null for "follow the system language". */
    data class Option(val tag: String?, val labelRes: Int)

    val options: List<Option> = listOf(
        Option(null, R.string.language_system),
        Option("en", R.string.language_en),
        Option("uk", R.string.language_uk),
        Option("pl", R.string.language_pl),
    )

    /** Index in [options] of the language currently selected by the user. */
    fun currentIndex(): Int {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.size() == 0) return 0
        val language = locales[0]?.language ?: return 0
        val index = options.indexOfFirst { it.tag == language }
        return if (index >= 0) index else 0
    }

    fun select(index: Int) {
        val option = options[index]
        Timber.i("Language changed to %s", option.tag ?: "system default")
        val locales = if (option.tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(option.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
