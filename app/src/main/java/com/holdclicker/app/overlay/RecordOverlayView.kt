package com.holdclicker.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View

/**
 * A full-screen, touchable layer used while recording. After the countdown
 * it captures every pointer the user puts down (taps, holds, swipes and
 * several fingers at once) and forwards the raw motion events to the
 * [RecordManager]. Because the window sits at the screen origin, the view's
 * local coordinates equal absolute screen coordinates, which is what the
 * gesture dispatcher needs for playback.
 */
class RecordOverlayView(context: Context) : View(context) {

    var capturing: Boolean = false
    var countdownText: String? = null
        set(value) { field = value; invalidate() }

    var onMotion: ((MotionEvent) -> Unit)? = null

    private val activePointers = HashMap<Int, PointF>()

    private val dimPaint = Paint().apply { color = 0x33000000 }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = 0xFFFF4081.toInt()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x55FF4081
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val countdownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!capturing) return true // swallow taps during the countdown
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                activePointers[event.getPointerId(idx)] = PointF(event.getX(idx), event.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    activePointers[event.getPointerId(i)] = PointF(event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val idx = event.actionIndex
                activePointers.remove(event.getPointerId(idx))
            }
        }
        onMotion?.invoke(event)
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, dimPaint)

        if (countdownText != null) {
            countdownPaint.textSize = h * 0.18f
            val cy = h / 2f - (countdownPaint.descent() + countdownPaint.ascent()) / 2f
            canvas.drawText(countdownText!!, w / 2f, cy, countdownPaint)
            hintPaint.textSize = h * 0.022f
            canvas.drawText("Get ready to record…", w / 2f, h * 0.62f, hintPaint)
            return
        }

        for (p in activePointers.values) {
            canvas.drawCircle(p.x, p.y, 60f, fillPaint)
            canvas.drawCircle(p.x, p.y, 60f, ringPaint)
        }
        hintPaint.textSize = h * 0.020f
        canvas.drawText(
            "Recording — tap, hold or swipe (multi-touch supported)",
            w / 2f, h * 0.06f, hintPaint
        )
    }
}
