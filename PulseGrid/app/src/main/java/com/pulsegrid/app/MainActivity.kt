package com.pulsegrid.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Setup screen: grant permissions, then start/stop the overlay service. */
class MainActivity : Activity() {

    private lateinit var overlayStatus: TextView
    private lateinit var usageStatus: TextView
    private lateinit var startBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(40), dp(22), dp(28))
            setBackgroundColor(Theme.bgDeep)
        }
        root.addView(label("PULSEGRID", 26f, Theme.textPrimary, bold = true))
        root.addView(
            label("In-game performance overlay", 13f, Theme.textDim),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(4); bottomMargin = dp(24) }
        )

        overlayStatus = permCard(
            root,
            "Display over other apps",
            "Required. Lets PulseGrid draw the side panels and crosshair on top of your games."
        ) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        usageStatus = permCard(
            root,
            "Usage access (optional)",
            "Needed only for the Recent Apps list."
        ) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        startBtn = label("Start overlay", 15f, Theme.onEmber, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundBg(Theme.ember, 12f)
            setOnClickListener { toggleService() }
        }
        root.addView(
            startBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) }
        )
        root.addView(
            label(
                "Open a game, then tap the small arrow tab on the left or right edge of the screen to open the tools.",
                12f, Theme.textFaint
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(16) }
        )

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun permCard(root: LinearLayout, title: String, desc: String, onGrant: () -> Unit): TextView {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundBg(Theme.bgRaised, 14f, Theme.line, 1)
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(
            label(title, 14f, Theme.textPrimary, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val status = label("…", 12f, Theme.textDim, mono = true)
        head.addView(status)
        card.addView(head)
        card.addView(
            label(desc, 12f, Theme.textDim),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) }
        )
        val btn = label("Open settings", 12f, Theme.textPrimary, bold = true).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundBg(Theme.bgDeep, 9f, Theme.lineBright, 1)
            setOnClickListener { onGrant() }
        }
        card.addView(
            btn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) }
        )
        root.addView(
            card,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(12) }
        )
        return status
    }

    private fun refresh() {
        val overlayOk = Settings.canDrawOverlays(this)
        overlayStatus.text = if (overlayOk) "GRANTED" else "NOT GRANTED"
        overlayStatus.setTextColor(if (overlayOk) Theme.good else Theme.ember)

        val usageOk = RecentApps.hasAccess(this)
        usageStatus.text = if (usageOk) "GRANTED" else "NOT GRANTED"
        usageStatus.setTextColor(if (usageOk) Theme.good else Theme.amber)

        startBtn.text = if (OverlayService.running) "Stop overlay" else "Start overlay"
    }

    private fun toggleService() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        val i = Intent(this, OverlayService::class.java)
        if (OverlayService.running) stopService(i) else startForegroundService(i)
        startBtn.postDelayed({ refresh() }, 400)
    }
}
