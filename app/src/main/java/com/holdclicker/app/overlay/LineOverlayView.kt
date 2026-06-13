package com.holdclicker.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.view.View

/**
 * A full-screen, untouchable overlay that draws dashed lines from each
 * swipe action's start point to its end point.
 */
class LineOverlayView(context: Context) : View(context) {

    /** Supplies (start, end) point pairs in screen coordinates. */
    var provider: (() -> List<Pair<PointF, PointF>>)? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF4081.toInt()
        strokeWidth = 6f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(22f, 14f), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pairs = provider?.invoke() ?: return
        for ((start, end) in pairs) {
            canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
        }
    }
}
