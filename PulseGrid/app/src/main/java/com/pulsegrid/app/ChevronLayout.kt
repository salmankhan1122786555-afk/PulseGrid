package com.pulsegrid.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.LinearLayout

/** Vertical panel clipped to an angled "chevron" edge that points toward the screen centre. */
class ChevronLayout(context: Context, private val fromLeft: Boolean) : LinearLayout(context) {

    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.bgPanel }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Theme.lineBright
        strokeWidth = context.dpf(1.2f)
    }

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val wf = w.toFloat()
        val hf = h.toFloat()
        path.reset()
        if (fromLeft) {
            path.moveTo(0f, 0f)
            path.lineTo(wf * 0.74f, 0f)
            path.lineTo(wf, hf / 2f)
            path.lineTo(wf * 0.74f, hf)
            path.lineTo(0f, hf)
        } else {
            path.moveTo(wf, 0f)
            path.lineTo(wf * 0.26f, 0f)
            path.lineTo(0f, hf / 2f)
            path.lineTo(wf * 0.26f, hf)
            path.lineTo(wf, hf)
        }
        path.close()
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(path)
        canvas.drawPath(path, fill)
        super.dispatchDraw(canvas)
        canvas.restore()
        canvas.drawPath(path, border)
    }
}
