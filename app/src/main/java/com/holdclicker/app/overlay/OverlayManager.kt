package com.holdclicker.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.holdclicker.app.R
import com.holdclicker.app.data.Prefs
import com.holdclicker.app.model.ActionType
import com.holdclicker.app.model.ClickerConfig
import com.holdclicker.app.model.Mode
import com.holdclicker.app.model.TargetAction
import com.holdclicker.app.service.AutoClickService
import com.holdclicker.app.ui.SettingsActivity
import kotlin.math.roundToInt

/**
 * Owns every floating window: the control bar, the numbered target
 * circles (plus pink end points for swipes) and the swipe line layer.
 * All windows use TYPE_ACCESSIBILITY_OVERLAY, so no extra "draw over
 * other apps" permission is needed.
 */
class OverlayManager(private val service: AutoClickService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var bar: View? = null
    private var lineView: LineOverlayView? = null

    private val targetViews = mutableListOf<View>()
    private val targetParams = mutableListOf<WindowManager.LayoutParams>()

    private var config: ClickerConfig? = null
    private var running = false

    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun overlayType() = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    fun show(cfg: ClickerConfig) {
        hide()
        config = cfg
        addLineOverlay()
        addControlBar()
        rebuildTargets()
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        removeAllTargets()
        lineView?.let { safeRemove(it) }
        lineView = null
        bar?.let { safeRemove(it) }
        bar = null
        running = false
    }

    private fun safeRemove(v: View) {
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- lines

    private fun addLineOverlay() {
        val v = LineOverlayView(service)
        v.provider = {
            config?.actions
                ?.filter { it.type == ActionType.SWIPE }
                ?.map { PointF(it.x, it.y) to PointF(it.endX, it.endY) }
                ?: emptyList()
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = 0
        try {
            wm.addView(v, lp)
            lineView = v
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ bar

    @SuppressLint("InflateParams")
    private fun addControlBar() {
        val v = LayoutInflater.from(service).inflate(R.layout.overlay_control_bar, null)
        val scale = Prefs.barScalePercent(service) / 100f
        val ids = intArrayOf(
            R.id.btnDrag, R.id.txtStatus, R.id.btnPlay, R.id.btnStop,
            R.id.btnAdd, R.id.btnRemove, R.id.btnSettings, R.id.btnClose
        )
        for (id in ids) {
            v.findViewById<TextView>(id).textSize = 18f * scale
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            baseFlags,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 30
        lp.y = 200

        makeDraggable(v.findViewById(R.id.btnDrag), lp, v, null)

        v.findViewById<TextView>(R.id.btnPlay).setOnClickListener { service.startAutomation() }
        v.findViewById<TextView>(R.id.btnStop).setOnClickListener { service.stopAutomation() }

        val add = v.findViewById<TextView>(R.id.btnAdd)
        val remove = v.findViewById<TextView>(R.id.btnRemove)
        if (config?.mode == Mode.SINGLE) {
            add.visibility = View.GONE
            remove.visibility = View.GONE
        }
        add.setOnClickListener {
            if (running) return@setOnClickListener
            val c = config ?: return@setOnClickListener
            val n = c.actions.size
            val dm = service.resources.displayMetrics
            c.actions.add(
                TargetAction(
                    x = dm.widthPixels * 0.3f + (n % 4) * 90f,
                    y = dm.heightPixels * 0.4f + (n % 4) * 90f
                )
            )
            rebuildTargets()
        }
        remove.setOnClickListener {
            if (running) return@setOnClickListener
            val c = config ?: return@setOnClickListener
            if (c.actions.isNotEmpty()) {
                c.actions.removeAt(c.actions.size - 1)
                rebuildTargets()
            }
        }

        v.findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            val i = Intent(service, SettingsActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(i)
        }
        v.findViewById<TextView>(R.id.btnClose).setOnClickListener { service.hideOverlay() }

        try {
            wm.addView(v, lp)
            bar = v
        } catch (e: Exception) {
            Toast.makeText(service, "Could not show overlay", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------------------------------------- targets

    /** Recreates every target window from the current configuration. */
    fun rebuildTargets() {
        removeAllTargets()
        val cfg = config ?: return
        val sizePx = (Prefs.targetSizeDp(service) * service.resources.displayMetrics.density).roundToInt()
        cfg.actions.forEachIndexed { index, action ->
            addTargetWindow(action, index, sizePx, isEnd = false)
            if (action.type == ActionType.SWIPE) {
                addTargetWindow(action, index, sizePx, isEnd = true)
            }
        }
        lineView?.invalidate()
    }

    private fun addTargetWindow(a: TargetAction, index: Int, sizePx: Int, isEnd: Boolean) {
        val v = TargetView(service)
        v.label = (index + 1).toString()
        v.isEndPoint = isEnd
        v.marker = when {
            isEnd -> "›"
            a.type == ActionType.HOLD -> "H"
            else -> null
        }
        val lp = WindowManager.LayoutParams(
            sizePx, sizePx, overlayType(), currentTargetFlags(), PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val cx = if (isEnd) a.endX else a.x
        val cy = if (isEnd) a.endY else a.y
        lp.x = (cx - sizePx / 2f).roundToInt()
        lp.y = (cy - sizePx / 2f).roundToInt()

        makeDraggable(v, lp, v) {
            val nx = lp.x + sizePx / 2f
            val ny = lp.y + sizePx / 2f
            if (isEnd) {
                a.endX = nx
                a.endY = ny
            } else {
                a.x = nx
                a.y = ny
            }
            lineView?.invalidate()
        }

        try {
            wm.addView(v, lp)
            targetViews.add(v)
            targetParams.add(lp)
        } catch (_: Exception) {
        }
    }

    private fun currentTargetFlags(): Int =
        if (running) baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else baseFlags

    private fun removeAllTargets() {
        targetViews.forEach { safeRemove(it) }
        targetViews.clear()
        targetParams.clear()
    }

    // ---------------------------------------------------------------- state

    /**
     * While running, target circles become untouchable so the dispatched
     * gestures pass through them to the app underneath.
     */
    fun setRunning(isRunning: Boolean) {
        running = isRunning
        targetViews.forEachIndexed { i, v ->
            val lp = targetParams[i]
            lp.flags = currentTargetFlags()
            try {
                wm.updateViewLayout(v, lp)
            } catch (_: Exception) {
            }
        }
        if (!isRunning) {
            handler.removeCallbacksAndMessages(null)
            bar?.findViewById<TextView>(R.id.txtStatus)?.text = "●"
        }
    }

    /** Shows the pre-start countdown in the bar's status field. */
    fun startCountdown(totalMs: Long) {
        val status = bar?.findViewById<TextView>(R.id.txtStatus) ?: return
        val seconds = (totalMs / 1000L).toInt()
        for (i in 0 until seconds) {
            handler.postDelayed({ status.text = (seconds - i).toString() }, i * 1000L)
        }
        handler.postDelayed({ status.text = "RUN" }, totalMs)
    }

    // ----------------------------------------------------------------- drag

    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(
        handle: View,
        lp: WindowManager.LayoutParams,
        windowView: View,
        onMove: (() -> Unit)?
    ) {
        handle.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x
                        startY = lp.y
                        touchX = e.rawX
                        touchY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = startX + (e.rawX - touchX).roundToInt()
                        lp.y = startY + (e.rawY - touchY).roundToInt()
                        try {
                            wm.updateViewLayout(windowView, lp)
                        } catch (_: Exception) {
                        }
                        onMove?.invoke()
                    }
                }
                return true
            }
        })
    }
}
