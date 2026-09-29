package com.pulsegrid.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.io.File

/**
 * Reads what a normal (non-root) app is allowed to see.
 * CPU/GPU clocks come from sysfs, which many phones block for regular apps —
 * when a read fails the value is null and the UI shows "N/A".
 */
object SystemStats {

    data class Snapshot(
        val cpuGHz: Float?,
        val cpuFraction: Float,
        val gpuMHz: Int?,
        val gpuFraction: Float,
        val tempC: Float,
        val batteryPct: Int,
        val state: PerfState,
        val thermalText: String
    )

    private var gpuPeak = 1
    private val thermalNames = arrayOf("None", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown")

    private fun readLong(path: String): Long? = try {
        File(path).readText().trim().toLong()
    } catch (e: Exception) {
        null
    }

    fun read(ctx: Context): Snapshot {
        // ---- CPU (kHz in sysfs) ----
        var curMax = 0L
        var hwMax = 0L
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            val cur = readLong(base + "scaling_cur_freq") ?: continue
            if (cur > curMax) curMax = cur
            val mx = readLong(base + "cpuinfo_max_freq") ?: 0L
            if (mx > hwMax) hwMax = mx
        }
        val cpuGHz = if (curMax > 0) curMax / 1_000_000f else null
        val cpuFrac = if (curMax > 0 && hwMax > 0) curMax.toFloat() / hwMax else 0f

        // ---- GPU (vendor specific paths, units vary) ----
        val gpuPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/misc/mali0/device/clock",
            "/sys/class/devfreq/gpufreq/cur_freq",
            "/sys/kernel/gpu/gpu_clock"
        )
        var gpuMHz: Int? = null
        for (path in gpuPaths) {
            val raw = readLong(path) ?: continue
            if (raw <= 0) continue
            gpuMHz = when {
                raw > 1_000_000L -> (raw / 1_000_000L).toInt()
                raw > 10_000L -> (raw / 1_000L).toInt()
                else -> raw.toInt()
            }
            break
        }
        if (gpuMHz != null && gpuMHz > gpuPeak) gpuPeak = gpuMHz
        val gpuFrac = if (gpuMHz != null) gpuMHz.toFloat() / gpuPeak else 0f

        // ---- battery ----
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempC = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (scale > 0) level * 100 / scale else 0

        // ---- performance colour state: worse of battery temp and system thermal status ----
        val tempState = when {
            tempC < 38f -> PerfState.GOOD
            tempC < 42f -> PerfState.WARN
            else -> PerfState.HOT
        }
        var thermalIdx = 0
        if (Build.VERSION.SDK_INT >= 29) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            thermalIdx = pm.currentThermalStatus.coerceIn(0, thermalNames.size - 1)
        }
        val thermalState = when {
            thermalIdx <= 1 -> PerfState.GOOD
            thermalIdx == 2 -> PerfState.WARN
            else -> PerfState.HOT
        }
        val state = if (thermalState.ordinal > tempState.ordinal) thermalState else tempState

        return Snapshot(cpuGHz, cpuFrac, gpuMHz, gpuFrac, tempC, pct, state, thermalNames[thermalIdx])
    }
}
