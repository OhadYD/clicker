package com.holdclicker.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.holdclicker.app.R
import com.holdclicker.app.data.Prefs
import com.holdclicker.app.model.ActionType
import com.holdclicker.app.model.ClickerConfig
import com.holdclicker.app.model.TargetAction
import com.holdclicker.app.overlay.OverlayManager
import com.holdclicker.app.ui.MainActivity

/**
 * The Accessibility service that dispatches gestures (tap / hold / swipe)
 * and hosts the floating overlay. Everything is user-controlled: nothing
 * runs until the user presses Start on the overlay, and Stop halts it
 * immediately.
 */
class AutoClickService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoClickService? = null
        const val NOTIF_ID = 1
        const val CHANNEL_ID = "running"
    }

    private val handler = Handler(Looper.getMainLooper())

    var overlay: OverlayManager? = null
        private set
    private var runner: AutomationRunner? = null
    var currentConfig: ClickerConfig? = null
        private set

    val isRunning: Boolean get() = runner?.running == true

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used; this app only dispatches gestures the user configured.
    }

    override fun onInterrupt() {
        stopAutomation()
    }

    override fun onDestroy() {
        stopAutomation()
        overlay?.hide()
        overlay = null
        instance = null
        super.onDestroy()
    }

    /** Shows (or replaces) the overlay for the given configuration. */
    fun showOverlay(config: ClickerConfig) {
        stopAutomation()
        currentConfig = config
        if (overlay == null) overlay = OverlayManager(this)
        overlay?.show(config)
    }

    fun hideOverlay() {
        stopAutomation()
        overlay?.hide()
    }

    fun startAutomation() {
        val cfg = currentConfig ?: return
        if (isRunning) return
        if (cfg.actions.isEmpty()) {
            Toast.makeText(this, "Add at least one target first (＋ button)", Toast.LENGTH_SHORT).show()
            return
        }
        // 1 second safety delay, plus an optional 3 second visible countdown.
        val delay = 1000L + if (Prefs.countdownEnabled(this)) 3000L else 0L
        showRunningNotification()
        vibrateIfEnabled()
        overlay?.setRunning(true)
        overlay?.startCountdown(delay)
        runner = AutomationRunner(this, cfg) { onRunnerStopped() }
        runner?.start(delay)
    }

    /** Stops immediately. */
    fun stopAutomation() {
        runner?.stop()
    }

    private fun onRunnerStopped() {
        runner = null
        cancelNotification()
        vibrateIfEnabled()
        overlay?.setRunning(false)
    }

    /**
     * Dispatches a single gesture for the action and invokes [onDone] when
     * the system reports the gesture finished (or was cancelled).
     */
    fun dispatchAction(a: TargetAction, durationMs: Long, onDone: () -> Unit) {
        val path = Path()
        val x = a.x.coerceAtLeast(1f)
        val y = a.y.coerceAtLeast(1f)
        path.moveTo(x, y)
        if (a.type == ActionType.SWIPE) {
            path.lineTo(a.endX.coerceAtLeast(1f), a.endY.coerceAtLeast(1f))
        }
        val duration = durationMs.coerceIn(1L, 59_000L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = onDone()
                override fun onCancelled(gestureDescription: GestureDescription?) = onDone()
            }, handler)
        } catch (e: Exception) {
            false
        }
        if (!dispatched) {
            // Keep the loop alive even if a dispatch is rejected.
            handler.postDelayed(onDone, duration + 50L)
        }
    }

    fun vibrateIfEnabled() {
        if (!Prefs.vibrationEnabled(this)) return
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(80L)
            }
        } catch (_: Exception) {
        }
    }

    private fun showRunningNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Automation running", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("HoldClicker is running")
            .setContentText("Automation in progress. Use the overlay Stop button to stop.")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(NOTIF_ID, n)
        } catch (_: Exception) {
        }
    }

    private fun cancelNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
        } catch (_: Exception) {
        }
    }
}
