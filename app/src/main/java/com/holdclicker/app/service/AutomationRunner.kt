package com.holdclicker.app.service

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.holdclicker.app.model.ActionType
import com.holdclicker.app.model.ClickerConfig
import com.holdclicker.app.model.Mode
import com.holdclicker.app.model.StopMode
import kotlin.math.max

/**
 * Runs a configuration: executes actions strictly in order, waiting for
 * each gesture to finish before starting the next, and repeats according
 * to the stop condition.
 *
 * Timing model:
 *  - Single mode: effective period per repeat = max(intervalMs, gesture duration).
 *    If a hold is longer than the interval, the runner simply waits for the
 *    hold to finish before pressing again.
 *  - Multi mode: per-action delayBefore/delayAfter are honored, and
 *    intervalMs is the pause between full cycles.
 */
class AutomationRunner(
    private val service: AutoClickService,
    private val config: ClickerConfig,
    private val onStopped: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    var running = false
        private set

    private var startedAt = 0L
    private var cycles = 0L

    fun start(prestartDelayMs: Long) {
        running = true
        handler.postDelayed({
            if (!running) return@postDelayed
            startedAt = SystemClock.elapsedRealtime()
            if (config.stopMode == StopMode.TIME) {
                handler.postDelayed({ stop() }, max(0L, config.stopTimeMs))
            }
            runAction(0)
        }, max(0L, prestartDelayMs))
    }

    /** Stops immediately and cancels every pending step. */
    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        onStopped()
    }

    private fun shouldStop(): Boolean = when (config.stopMode) {
        StopMode.INFINITE -> false
        StopMode.TIME -> SystemClock.elapsedRealtime() - startedAt >= config.stopTimeMs
        StopMode.CYCLES -> cycles >= config.stopCycles
    }

    private fun gestureDuration(type: ActionType, holdMs: Long, swipeMs: Long): Long = when (type) {
        ActionType.TAP -> 50L
        ActionType.HOLD -> max(1L, holdMs)
        ActionType.SWIPE -> max(1L, swipeMs)
    }

    private fun runAction(index: Int) {
        if (!running) return
        if (index >= config.actions.size) {
            cycles++
            if (shouldStop()) {
                stop()
                return
            }
            val betweenCycles = if (config.mode == Mode.MULTI) max(0L, config.intervalMs) else 0L
            handler.postDelayed({ runAction(0) }, betweenCycles)
            return
        }
        val a = config.actions[index]
        val before = if (config.mode == Mode.MULTI) max(0L, a.delayBeforeMs) else 0L
        handler.postDelayed({
            if (!running) return@postDelayed
            val dur = gestureDuration(a.type, a.holdMs, a.swipeMs)
            service.dispatchAction(a, dur) {
                if (!running) return@dispatchAction
                val after = if (config.mode == Mode.SINGLE) {
                    // Wait the remaining interval; if the hold was longer than
                    // the interval this is 0 and we continue right away.
                    max(0L, config.intervalMs - dur)
                } else {
                    max(0L, a.delayAfterMs)
                }
                handler.postDelayed({ runAction(index + 1) }, after)
            }
        }, before)
    }
}
