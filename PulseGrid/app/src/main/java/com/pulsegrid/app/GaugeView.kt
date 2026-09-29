package com.pulsegrid.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** Half-circle gauge whose colour follows the current performance state. */
class GaugeView(
    context: Context,
    private val labelText: String,
    private val unitText: String
) : View(context) {

    var fraction: Float = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var valueText: String = "--"
        set(v) { field = v; invalidate() }
    var arcColor: Int = Theme.good
        set(v) { field = v; invalidate() }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF221D29.toInt()
        strokeWidth = context.dpf(8f)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = context.dpf(8f)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = context.dpf(20f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Theme.textDim
        textSize = context.dpf(10f)
        letterSpacing = 0.15f
    }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(120), context.dp(100))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = context.dpf(56f)
        val r = context.dpf(48f)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, 180f, 180f, false, track)
        arc.color = arcColor
        if (fraction > 0.01f) canvas.drawArc(rect, 180f, 180f * fraction, false, arc)
        valuePaint.color = arcColor
        canvas.drawText(valueText, cx, cy + context.dpf(22f), valuePaint)
        canvas.drawText("$labelText · $unitText", cx, cy + context.dpf(38f), labelPaint)
    }
}
