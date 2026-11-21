package com.example.minesweeper.util

import android.util.Log
import timber.log.Timber

/**
 * Central logging setup built on Timber.
 *
 *  - **Debug builds**: every message (VERBOSE and up) goes to Logcat, tagged with
 *    `ClassName:line`, which makes the log easy to filter and trace.
 *  - **Release builds**: only WARN and ERROR are written, so no gameplay details leak
 *    into the logs of production devices. This is also the place to forward errors
 *    to a crash-reporting service.
 *
 * Usage everywhere in the app: `Timber.d("Revealed (%d, %d)", row, col)`.
 */
object AppLogger {

    fun init(debug: Boolean) {
        Timber.uprootAll()
        if (debug) {
            Timber.plant(LineNumberDebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        Timber.i("Logger initialised (debug=%b)", debug)
    }
}

/** Debug tree that appends the source line number to the tag. */
class LineNumberDebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String? {
        return "${super.createStackElementTag(element)}:${element.lineNumber}"
    }
}

/** Release tree: writes only warnings and errors, including stack traces. */
class ReleaseTree : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val text = if (t != null) "$message\n${Log.getStackTraceString(t)}" else message
        Log.println(priority, tag ?: DEFAULT_TAG, text)
    }

    private companion object {
        const val DEFAULT_TAG = "Minesweeper"
    }
}
