package com.holdclicker.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.holdclicker.app.data.Prefs

/** Applies the user's chosen accent theme before the view is created. */
abstract class ThemedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Prefs.accentThemeRes(this))
        super.onCreate(savedInstanceState)
    }
}
