package com.holdclicker.app.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.holdclicker.app.R
import com.holdclicker.app.data.ConfigStore
import com.holdclicker.app.model.ActionType
import com.holdclicker.app.model.ClickerConfig
import com.holdclicker.app.model.Mode
import com.holdclicker.app.model.StopMode
import com.holdclicker.app.model.TargetAction
import com.holdclicker.app.service.AutoClickService
import com.holdclicker.app.ui.MultiTargetActivity
import kotlin.math.hypot

/**
 * Records a sequence of gestures the user performs on a transparent
 * capture layer, then converts them into an editable Multi Target config.
 * Overlapping presses become a simultaneous group (e.g. two-finger holds).
 */
class RecordManager(private val service: AutoClickService) {

    companion object {
        const val RECORDED_NAME = "Recorded sequence"
        private const val COUNTDOWN_SECONDS = 3
        private const val HOLD_THRESHOLD_MS = 350L
    }

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var recordView: RecordOverlayView? = null
    private var stopBar: View? = null

    private val slopPx = 24f * service.resources.displayMetrics.density

    private class Touch(
        val downAt: Long,
        val sx: Float,
        val sy: Float
    ) {
        var ex: Float = sx
        var ey: Float = sy
        var upAt: Long = downAt
        var moved: Boolean = false
    }

    private val inProgress = HashMap<Int, Touch>()
    private val done = mutableListOf<Touch>()
    private var recordStart = 0L

    private val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    fun start() {
        addRecordView()
        addStopBar()
        startCountdown()
    }

    private fun addRecordView() {
        val v = RecordOverlayView(service)
        v.onMotion = { handleMotion(it) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = 0
        try {
            wm.addView(v, lp)
            recordView = v
        } catch (_: Exception) {
        }
    }

    private fun addStopBar() {
        val bar = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_control_bar)
            setPadding(24, 12, 24, 12)
        }
        val rec = TextView(service).apply {
            text = "⏺ REC"
            setTextColor(0xFFFF4081.toInt())
            textSize = 18f
            setPadding(8, 8, 24, 8)
        }
        val stop = TextView(service).apply {
            text = "■ Stop"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(8, 8, 8, 8)
            setOnClickListener { stop() }
        }
        bar.addView(rec)
        bar.addView(stop)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = 120
        try {
            wm.addView(bar, lp)
            stopBar = bar
        } catch (_: Exception) {
        }
    }

    private fun startCountdown() {
        val v = recordView ?: return
        v.capturing = false
        for (i in 0 until COUNTDOWN_SECONDS) {
            handler.postDelayed({ v.countdownText = (COUNTDOWN_SECONDS - i).toString() }, i * 1000L)
        }
        handler.postDelayed({
            v.countdownText = null
            v.capturing = true
            recordStart = SystemClock.elapsedRealtime()
        }, COUNTDOWN_SECONDS * 1000L)
    }

    private fun now() = SystemClock.elapsedRealtime() - recordStart

    private fun handleMotion(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                val id = e.getPointerId(idx)
                inProgress[id] = Touch(now(), e.getX(idx), e.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val t = inProgress[e.getPointerId(i)] ?: continue
                    t.ex = e.getX(i)
                    t.ey = e.getY(i)
                    if (hypot(t.ex - t.sx, t.ey - t.sy) > slopPx) t.moved = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = e.actionIndex
                val id = e.getPointerId(idx)
                val t = inProgress.remove(id) ?: return
                t.ex = e.getX(idx)
                t.ey = e.getY(idx)
                t.upAt = now()
                if (hypot(t.ex - t.sx, t.ey - t.sy) > slopPx) t.moved = true
                done.add(t)
            }
            MotionEvent.ACTION_CANCEL -> inProgress.clear()
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        recordView?.let { safeRemove(it) }
        stopBar?.let { safeRemove(it) }
        recordView = null
        stopBar = null

        val cfg = buildConfig()
        service.onRecordingFinished()

        if (cfg == null || cfg.actions.isEmpty()) {
            Toast.makeText(service, "Nothing recorded", Toast.LENGTH_SHORT).show()
            return
        }
        ConfigStore.save(service, cfg)
        Toast.makeText(
            service, "Recorded ${cfg.actions.size} action(s) — opening editor",
            Toast.LENGTH_LONG
        ).show()
        val i = Intent(service, MultiTargetActivity::class.java)
        i.putExtra("configName", RECORDED_NAME)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(i)
    }

    private fun buildConfig(): ClickerConfig? {
        if (done.isEmpty()) return null
        val sorted = done.sortedBy { it.downAt }

        // Cluster temporally overlapping touches into simultaneous groups.
        val groups = mutableListOf<MutableList<Touch>>()
        for (t in sorted) {
            val g = groups.lastOrNull()
            val groupEnd = g?.maxOfOrNull { it.upAt }
            if (g != null && groupEnd != null && t.downAt < groupEnd) {
                g.add(t)
            } else {
                groups.add(mutableListOf(t))
            }
        }

        val actions = mutableListOf<TargetAction>()
        var prevGroupEnd = 0L
        groups.forEachIndexed { gi, group ->
            val ordered = group.sortedBy { it.downAt }
            val groupStart = ordered.first().downAt
            val delayBefore = if (gi == 0) 0L else (groupStart - prevGroupEnd).coerceAtLeast(0L)
            ordered.forEachIndexed { ti, t ->
                val action = toAction(t)
                action.delayBeforeMs = if (ti == 0) delayBefore else 0L
                action.delayAfterMs = 0L
                action.simultaneousWithNext = ti < ordered.size - 1
                actions.add(action)
            }
            prevGroupEnd = group.maxOf { it.upAt }
        }

        return ClickerConfig(
            name = RECORDED_NAME,
            mode = Mode.MULTI,
            intervalMs = 0L,
            stopMode = StopMode.INFINITE,
            actions = actions
        )
    }

    private fun toAction(t: Touch): TargetAction {
        val dur = (t.upAt - t.downAt).coerceAtLeast(1L)
        return when {
            t.moved -> TargetAction(
                x = t.sx, y = t.sy, type = ActionType.SWIPE,
                endX = t.ex, endY = t.ey, swipeMs = dur
            )
            dur >= HOLD_THRESHOLD_MS -> TargetAction(
                x = t.sx, y = t.sy, type = ActionType.HOLD, holdMs = dur
            )
            else -> TargetAction(x = t.sx, y = t.sy, type = ActionType.TAP)
        }
    }

    private fun safeRemove(v: View) {
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }
}
