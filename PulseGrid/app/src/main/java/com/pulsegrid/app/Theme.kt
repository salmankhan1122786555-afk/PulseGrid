package com.pulsegrid.app

import android.graphics.Color

object Theme {
    val bgPanel = Color.parseColor("#E6120F17")
    val bgRaised = Color.parseColor("#FF1A1622")
    val bgDeep = Color.parseColor("#FF09080C")
    val line = Color.parseColor("#FF2B2634")
    val lineBright = Color.parseColor("#FF4A3F52")
    val ember = Color.parseColor("#FFFF4630")
    val amber = Color.parseColor("#FFFFB020")
    val good = Color.parseColor("#FF2EE6A0")
    val textPrimary = Color.parseColor("#FFF3F0EE")
    val textDim = Color.parseColor("#FF8D8794")
    val textFaint = Color.parseColor("#FF5C5665")
    val onEmber = Color.parseColor("#FF1A0704")
}

/** Performance colour states — green = cool, amber = boosting, red = thermal limit. */
enum class PerfState(val color: Int, val label: String) {
    GOOD(Theme.good, "Optimal"),
    WARN(Theme.amber, "Boosting"),
    HOT(Theme.ember, "Thermal limit")
}
