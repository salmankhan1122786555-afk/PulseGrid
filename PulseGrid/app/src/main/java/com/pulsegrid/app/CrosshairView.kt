package com.pulsegrid.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class CrosshairView(context: Context) : View(context) {

    enum class Style(val title: String) {
        DOT("Dot"), CROSS("Cross"), RING("Ring"), CHEVRON("Chevron")
    }

    var style: Style = Style.CROSS
        set(v) { field = v; invalidate() }
    var color: Int = Theme.ember
        set(v) { field = v; invalidate() }
    var sizeDp: Int = 28
        set(v) { field = v; invalidate() }
    var opacityPct: Int = 90
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = context.dpf(sizeDp.toFloat()) / 2f
        p.color = color
        p.alpha = opacityPct * 255 / 100
        p.strokeWidth = context.dpf(2.5f)
        val dot = context.dpf(1.8f)

        when (style) {
            Style.DOT -> {
                p.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, s * 0.3f, p)
            }
            Style.CROSS -> {
                p.style = Paint.Style.STROKE
                canvas.drawLine(cx - s, cy, cx - s * 0.35f, cy, p)
                canvas.drawLine(cx + s * 0.35f, cy, cx + s, cy, p)
                canvas.drawLine(cx, cy - s, cx, cy - s * 0.35f, p)
                canvas.drawLine(cx, cy + s * 0.35f, cx, cy + s, p)
                p.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, dot, p)
            }
            Style.RING -> {
                p.style = Paint.Style.STROKE
                canvas.drawCircle(cx, cy, s * 0.8f, p)
                p.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, dot, p)
            }
            Style.CHEVRON -> {
                p.style = Paint.Style.STROKE
                p.strokeJoin = Paint.Join.ROUND
                path.reset()
                path.moveTo(cx - s * 0.7f, cy + s * 0.5f)
                path.lineTo(cx, cy - s * 0.4f)
                path.lineTo(cx + s * 0.7f, cy + s * 0.5f)
                canvas.drawPath(path, p)
                p.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, dot, p)
            }
        }
    }
}
