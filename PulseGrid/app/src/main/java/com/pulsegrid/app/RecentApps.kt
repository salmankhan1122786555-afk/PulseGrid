package com.pulsegrid.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process

object RecentApps {

    data class Item(val pkg: String, val label: String, val icon: Drawable, val lastUsed: Long)

    /** Needs the user to grant "Usage access" to PulseGrid in system settings. */
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun load(ctx: Context, limit: Int): List<Item> {
        if (!hasAccess(ctx)) return emptyList()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3L * 24 * 3600 * 1000, now)
            ?: return emptyList()
        val pm = ctx.packageManager
        val home = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        )?.activityInfo?.packageName

        return stats.asSequence()
            .filter {
                it.lastTimeUsed > 0 &&
                    it.packageName != ctx.packageName &&
                    it.packageName != home &&
                    pm.getLaunchIntentForPackage(it.packageName) != null
            }
            .sortedByDescending { it.lastTimeUsed }
            .distinctBy { it.packageName }
            .take(limit)
            .mapNotNull {
                try {
                    val ai = pm.getApplicationInfo(it.packageName, 0)
                    Item(it.packageName, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai), it.lastTimeUsed)
                } catch (e: Exception) {
                    null
                }
            }
            .toList()
    }

    fun ago(t: Long): String {
        val m = (System.currentTimeMillis() - t) / 60000
        return when {
            m < 1 -> "Just now"
            m < 60 -> "${m}m ago"
            m < 1440 -> "${m / 60}h ago"
            else -> "${m / 1440}d ago"
        }
    }
}
