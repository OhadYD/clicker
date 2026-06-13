package com.holdclicker.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.holdclicker.app.R
import com.holdclicker.app.model.ActionType
import com.holdclicker.app.model.ClickerConfig
import com.holdclicker.app.model.TargetAction
import kotlin.math.max

/**
 * A schematic of the configuration: a Start node branches into one column
 * per lane, each lane a vertical chain of action nodes joined by arrows,
 * with a loop arrow showing the cycle repeats. Purely illustrative.
 */
class LaneTreeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var config: ClickerConfig? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )

    private val nodeH = dp(46f)
    private val rowGap = dp(26f)
    private val topGap = dp(28f)
    private val startH = dp(40f)

    private val textMain = ContextCompat.getColor(context, R.color.textMain)
    private val cardColor = ContextCompat.getColor(context, R.color.card)

    private val palette = intArrayOf(
        0xFF03DAC5.toInt(), 0xFFFFB300.toInt(), 0xFF7C4DFF.toInt(),
        0xFF40C4FF.toInt(), 0xFFFF4081.toInt(), 0xFF69F0AE.toInt()
    )

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val nodeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textMain
        textAlign = Paint.Align.CENTER
        textSize = dp(13f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(11f)
        isFakeBoldText = true
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = 0x88888888.toInt()
    }
    private val arrowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x88888888.toInt()
    }

    private fun laneColor(i: Int) = palette[i % palette.size]

    private fun maxLaneLen(): Int = config?.lanes?.maxOfOrNull { it.actions.size } ?: 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = max(1, maxLaneLen())
        val height = (topGap + startH + topGap + rows * (nodeH + rowGap) + dp(44f)).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cfg = config ?: return
        val lanes = cfg.lanes
        if (lanes.isEmpty()) return

        val w = width.toFloat()
        val cx = w / 2f

        // Start node
        val startTop = topGap
        val startBottom = topGap + startH
        drawPill(canvas, cx, (startTop + startBottom) / 2f, dp(120f), startH, 0xFF2E3440.toInt(),
            0xFFFFFFFF.toInt(), "▶ Start", 0xFFFFFFFF.toInt())

        val laneCount = lanes.size
        val firstRowCenterY = startBottom + topGap + nodeH / 2f

        lanes.forEachIndexed { li, lane ->
            val laneX = w * (li + 0.5f) / laneCount
            val color = laneColor(li)

            // Branch arrow from Start to this lane's first node (or to a label if empty)
            arrow(canvas, cx, startBottom, laneX, firstRowCenterY - nodeH / 2f)

            // lane letter caption
            labelPaint.color = color
            canvas.drawText(
                ('A' + li).toString(),
                laneX, startBottom + topGap * 0.7f, labelPaint
            )

            if (lane.actions.isEmpty()) {
                drawPill(canvas, laneX, firstRowCenterY, nodeWidth(laneCount), nodeH,
                    cardColor, color, "(empty)", textMain)
                return@forEachIndexed
            }

            lane.actions.forEachIndexed { ai, action ->
                val cy = firstRowCenterY + ai * (nodeH + rowGap)
                if (ai > 0) {
                    val prevCy = firstRowCenterY + (ai - 1) * (nodeH + rowGap)
                    arrow(canvas, laneX, prevCy + nodeH / 2f, laneX, cy - nodeH / 2f)
                }
                drawPill(canvas, laneX, cy, nodeWidth(laneCount), nodeH,
                    cardColor, color, nodeLabel(ai + 1, action), textMain)
            }

            // loop hint from last node downward
            val lastCy = firstRowCenterY + (lane.actions.size - 1) * (nodeH + rowGap)
            dashedDown(canvas, laneX, lastCy + nodeH / 2f, lastCy + nodeH / 2f + dp(20f))
        }

        // repeats caption at the bottom
        textPaint.color = 0xFF9AA3B2.toInt()
        canvas.drawText("⟲ repeats each cycle", cx, height - dp(12f), textPaint)
        textPaint.color = textMain
    }

    private fun nodeWidth(laneCount: Int): Float {
        val avail = width.toFloat() / laneCount
        return (avail - dp(16f)).coerceIn(dp(70f), dp(200f))
    }

    private fun nodeLabel(n: Int, a: TargetAction): String = when (a.type) {
        ActionType.TAP -> "$n  Tap"
        ActionType.HOLD -> if (a.holdIndefinite) "$n  Hold ∞" else "$n  Hold ${a.holdMs}ms"
        ActionType.SWIPE -> "$n  Swipe ${a.swipeMs}ms"
    }

    private fun drawPill(
        canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float,
        fill: Int, stroke: Int, text: String, textColor: Int
    ) {
        val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        nodePaint.color = fill
        canvas.drawRoundRect(rect, h / 2f, h / 2f, nodePaint)
        nodeStroke.color = stroke
        canvas.drawRoundRect(rect, h / 2f, h / 2f, nodeStroke)
        textPaint.color = textColor
        val ty = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, cx, ty, textPaint)
        textPaint.color = textMain
    }

    private fun arrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        canvas.drawLine(x1, y1, x2, y2, arrowPaint)
        // simple arrowhead at (x2,y2)
        val ah = dp(6f)
        val path = Path()
        path.moveTo(x2, y2)
        path.lineTo(x2 - ah * 0.6f, y2 - ah)
        path.lineTo(x2 + ah * 0.6f, y2 - ah)
        path.close()
        canvas.drawPath(path, arrowFill)
    }

    private fun dashedDown(canvas: Canvas, x: Float, y1: Float, y2: Float) {
        var y = y1
        while (y < y2) {
            canvas.drawLine(x, y, x, (y + dp(5f)).coerceAtMost(y2), arrowPaint)
            y += dp(10f)
        }
    }
}
