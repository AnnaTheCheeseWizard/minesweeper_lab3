package com.example.minesweeper

import android.app.Application
import com.example.minesweeper.util.AppLogger

class MinesweeperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(BuildConfig.DEBUG)
    }
}
