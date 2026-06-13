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
}
