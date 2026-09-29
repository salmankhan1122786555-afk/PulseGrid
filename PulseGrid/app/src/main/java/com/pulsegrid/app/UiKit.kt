package com.pulsegrid.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.label(
    txt: String,
    sp: Float,
    color: Int,
    bold: Boolean = false,
    mono: Boolean = false
): TextView = TextView(this).apply {
    text = txt
    textSize = sp
    setTextColor(color)
    typeface = when {
        mono && bold -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        mono -> Typeface.MONOSPACE
        bold -> Typeface.DEFAULT_BOLD
        else -> Typeface.DEFAULT
    }
    includeFontPadding = false
}

fun Context.roundBg(
    fill: Int,
    radiusDp: Float,
    strokeColor: Int = 0,
    strokeDp: Int = 0
): GradientDrawable = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = dpf(radiusDp)
    if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
}
