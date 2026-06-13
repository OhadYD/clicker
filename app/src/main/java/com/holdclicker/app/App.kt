package com.holdclicker.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.holdclicker.app.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Prefs.nightModeDelegate(this))
    }
}
