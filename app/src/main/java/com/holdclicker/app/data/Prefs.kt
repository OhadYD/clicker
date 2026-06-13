package com.holdclicker.app.data

import android.content.Context

/** Common settings stored in SharedPreferences. */
object Prefs {

    private const val FILE = "settings"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun targetSizeDp(ctx: Context): Int = p(ctx).getInt("targetSizeDp", 56)
    fun setTargetSizeDp(ctx: Context, value: Int) {
        p(ctx).edit().putInt("targetSizeDp", value.coerceIn(24, 120)).apply()
    }

    fun barScalePercent(ctx: Context): Int = p(ctx).getInt("barScalePercent", 100)
    fun setBarScalePercent(ctx: Context, value: Int) {
        p(ctx).edit().putInt("barScalePercent", value.coerceIn(50, 200)).apply()
    }

    fun vibrationEnabled(ctx: Context): Boolean = p(ctx).getBoolean("vibration", true)
    fun setVibrationEnabled(ctx: Context, value: Boolean) {
        p(ctx).edit().putBoolean("vibration", value).apply()
    }

    fun countdownEnabled(ctx: Context): Boolean = p(ctx).getBoolean("countdown", false)
    fun setCountdownEnabled(ctx: Context, value: Boolean) {
        p(ctx).edit().putBoolean("countdown", value).apply()
    }

    // ---- Appearance ------------------------------------------------------

    /** 0 = Aqua, 1 = Ocean, 2 = Grape, 3 = Ember. */
    fun accentIndex(ctx: Context): Int = p(ctx).getInt("accent", 0)
    fun setAccentIndex(ctx: Context, value: Int) {
        p(ctx).edit().putInt("accent", value.coerceIn(0, 3)).apply()
    }

    fun accentThemeRes(ctx: Context): Int = when (accentIndex(ctx)) {
        1 -> com.holdclicker.app.R.style.Theme_HoldClicker_Ocean
        2 -> com.holdclicker.app.R.style.Theme_HoldClicker_Grape
        3 -> com.holdclicker.app.R.style.Theme_HoldClicker_Ember
        else -> com.holdclicker.app.R.style.Theme_HoldClicker_Aqua
    }

    /** 0 = follow system, 1 = light, 2 = dark. */
    fun nightModeIndex(ctx: Context): Int = p(ctx).getInt("nightMode", 0)
    fun setNightModeIndex(ctx: Context, value: Int) {
        p(ctx).edit().putInt("nightMode", value.coerceIn(0, 2)).apply()
    }

    fun nightModeDelegate(ctx: Context): Int = when (nightModeIndex(ctx)) {
        1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
