package com.holdclicker.app.service

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.holdclicker.app.model.ActionType
import com.holdclicker.app.model.ClickerConfig
import com.holdclicker.app.model.Mode
import com.holdclicker.app.model.StopMode
import com.holdclicker.app.model.TargetAction
import kotlin.math.max

/**
 * Runs a configuration. Every lane is an independent parallel branch with
 * its own timeline; all lanes start together at the beginning of each cycle.
 *
 * Because Android cancels an in-progress gesture when a new one is
 * dispatched, true concurrency (e.g. holding one spot while tapping another)
 * is achieved by compiling every lane's actions into a single multi-stroke
 * gesture per cycle, each stroke placed at its own start offset. Android
 * limits one gesture to getMaxStrokeCount() strokes and 60s, so one cycle
 * may contain at most that many actions in total.
 *
 * Timing:
 *  - Single mode: period per repeat = max(intervalMs, gesture duration).
 *  - Multi/parallel mode: per-action delayBefore/delayAfter shape each lane's
 *    timeline; intervalMs is the pause between full cycles.
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
            runCycle()
        }, max(0L, prestartDelayMs))
    }

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

    private fun gestureDuration(a: TargetAction): Long = when (a.type) {
        ActionType.TAP -> 50L
        ActionType.HOLD -> max(1L, a.holdMs)
        ActionType.SWIPE -> max(1L, a.swipeMs)
    }

    /** Builds the strokes for one cycle and returns them with the cycle length. */
    private fun compileCycle(): Pair<List<AutoClickService.TimedStroke>, Long> {
        val strokes = mutableListOf<AutoClickService.TimedStroke>()
        var cycleLen = 0L
        val multi = config.mode == Mode.MULTI
        for (lane in config.lanes) {
            var cursor = 0L
            for (a in lane.actions) {
                val before = if (multi) max(0L, a.delayBeforeMs) else 0L
                val start = cursor + before
                val dur = gestureDuration(a)
                strokes.add(
                    AutoClickService.TimedStroke(
                        a.x, a.y, a.endX, a.endY, a.type == ActionType.SWIPE, start, dur
                    )
                )
                val after = if (multi) max(0L, a.delayAfterMs) else 0L
                cursor = start + dur + after
                if (start + dur > cycleLen) cycleLen = start + dur
            }
        }
        return strokes to cycleLen
    }

    private fun runCycle() {
        if (!running) return
        val (strokes, cycleLen) = compileCycle()
        if (strokes.isEmpty()) {
            scheduleNext(0L)
            return
        }
        service.dispatchTimeline(strokes, cycleLen) {
            if (!running) return@dispatchTimeline
            val betweenCycles = if (config.mode == Mode.SINGLE) {
                max(0L, config.intervalMs - cycleLen)
            } else {
                max(0L, config.intervalMs)
            }
            scheduleNext(betweenCycles)
        }
    }

    private fun scheduleNext(delayMs: Long) {
        cycles++
        if (shouldStop()) {
            stop()
            return
        }
        handler.postDelayed({ runCycle() }, delayMs)
    }
}
