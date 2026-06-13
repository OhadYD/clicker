package com.holdclicker.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/**
 * A numbered target circle. Start points are teal, swipe end points are
 * pink. Hold targets show a small "H" marker; swipe end points show "›".
 */
class TargetView(context: Context) : View(context) {

    var label: String = "1"
        set(value) { field = value; invalidate() }

    var isEndPoint: Boolean = false
        set(value) { field = value; invalidate() }

    var marker: String? = null
        set(value) { field = value; invalidate() }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = (min(w, h) / 2f) - 4f
        fillPaint.color = if (isEndPoint) 0x99FF4081.toInt() else 0x9903DAC5.toInt()
        canvas.drawCircle(w / 2f, h / 2f, r, fillPaint)
        canvas.drawCircle(w / 2f, h / 2f, r, strokePaint)

        textPaint.textSize = h * 0.38f
        val ty = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, w / 2f, ty, textPaint)

        marker?.let {
            markerPaint.textSize = h * 0.24f
            canvas.drawText(it, w * 0.78f, h * 0.30f, markerPaint)
        }
    }
}
