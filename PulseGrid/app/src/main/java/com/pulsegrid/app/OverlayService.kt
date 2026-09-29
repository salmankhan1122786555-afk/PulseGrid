package com.pulsegrid.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/**
 * Draws PulseGrid over other apps using TYPE_APPLICATION_OVERLAY windows:
 *  - two small edge handles (always there while the service runs)
 *  - when opened: left panel, right panel, bottom status pill and a home button
 *  - optional crosshair window and colour-tint filter window (both touch-through)
 * The centre of the screen is never covered by a touchable window, so the game stays playable.
 */
class OverlayService : Service() {

    companion object {
        @Volatile var running = false
        const val ACTION_STOP = "com.pulsegrid.app.STOP"
        private const val CHANNEL = "pulsegrid_overlay"
        private const val NOTIF_ID = 1
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private class Tile(val root: LinearLayout, val cap: TextView)
    private data class Preset(val name: String, val info: String, val tint: Int)
    private data class Demo(val cpu: String, val cpuF: Float, val gpu: String, val gpuF: Float, val temp: String)

    private val presets = listOf(
        Preset("Off", "No colour overlay", 0),
        Preset("Warm Boost", "Warm tint, punchier reds", 0x26FF7A2F),
        Preset("Cinematic", "Cool teal wash for moody scenes", 0x2600B8A9),
        Preset("Night Ops", "Dimmed and cool, low glare", 0x59101A3A),
        Preset("Eye Comfort", "Amber tint, less blue light", 0x3DFFB020)
    )
    private val demos = mapOf(
        PerfState.GOOD to Demo("0.94", 0.30f, "210", 0.30f, "31°C"),
        PerfState.WARN to Demo("1.42", 0.55f, "396", 0.60f, "39°C"),
        PerfState.HOT to Demo("2.01", 0.95f, "512", 0.95f, "46°C")
    )

    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())

    // windows
    private var handleLeft: View? = null
    private var handleRight: View? = null
    private var leftWin: View? = null
    private var rightWin: View? = null
    private var statusWin: View? = null
    private var homeWin: View? = null
    private var crossWin: CrosshairView? = null
    private var filterWin: View? = null

    private var toolsOpen = false
    private var demoState: PerfState? = null

    // settings (kept in memory while the service runs)
    private var crossOn = false
    private var crossStyle = CrosshairView.Style.CROSS
    private var crossColor = Theme.ember
    private var crossSize = 28
    private var crossOpacity = 90
    private var presetIndex = 0

    // live views
    private var cpuGauge: GaugeView? = null
    private var gpuGauge: GaugeView? = null
    private var tempText: TextView? = null
    private var thermalText: TextView? = null
    private var statusDotBg: GradientDrawable? = null
    private var statusLabel: TextView? = null
    private var statusReadout: TextView? = null
    private var wifiTile: Tile? = null
    private var btTile: Tile? = null
    private var recentBox: LinearLayout? = null
    private var rightPages: List<View> = emptyList()

    // crosshair page views
    private var crossPreview: CrosshairView? = null
    private var crossToggle: TextView? = null
    private var sizeLabel: TextView? = null
    private var opacityLabel: TextView? = null
    private val styleChips = mutableListOf<Pair<CrosshairView.Style, TextView>>()
    private val colorDots = mutableListOf<Pair<Int, View>>()

    // filter page views
    private val presetRows = mutableListOf<LinearLayout>()

    private val tick = object : Runnable {
        override fun run() {
            if (!toolsOpen) return
            refreshStats()
            ui.postDelayed(this, 1000)
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground() // must happen quickly after startForegroundService()
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (handleLeft == null) addHandles()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        ui.removeCallbacksAndMessages(null)
        listOf(handleLeft, handleRight, leftWin, rightWin, statusWin, homeWin, crossWin, filterWin)
            .forEach { it?.let { v -> safeRemove(v) } }
        handleLeft = null; handleRight = null; leftWin = null; rightWin = null
        statusWin = null; homeWin = null; crossWin = null; filterWin = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "PulseGrid overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("PulseGrid overlay is running")
            .setContentText("Tap the edge handle in any game to open the tools")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(0, "Stop", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ------------------------------------------------------------------ windows

    private fun lp(w: Int, h: Int, gravity: Int, touchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
        ).also { it.gravity = gravity }
    }

    private fun safeRemove(v: View) {
        try { wm.removeView(v) } catch (e: Exception) { /* already gone */ }
    }

    private fun addHandles() {
        handleLeft = buildHandle(true).also {
            wm.addView(it, lp(dp(24), dp(84), Gravity.START or Gravity.CENTER_VERTICAL, true))
        }
        handleRight = buildHandle(false).also {
            wm.addView(it, lp(dp(24), dp(84), Gravity.END or Gravity.CENTER_VERTICAL, true))
        }
    }

    private fun buildHandle(left: Boolean): View {
        val r = dpf(12f)
        return label(if (left) "›" else "‹", 18f, Theme.textDim, bold = true).apply {
            gravity = Gravity.CENTER
            alpha = 0.65f
            background = GradientDrawable().apply {
                setColor(Theme.bgPanel)
                setStroke(dp(1), Theme.line)
                cornerRadii = if (left) floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
                else floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            }
            setOnClickListener { openTools() }
        }
    }

    private fun openTools() {
        if (toolsOpen) return
        toolsOpen = true
        handleLeft?.visibility = View.GONE
        handleRight?.visibility = View.GONE

        val pw = minOf(dp(300), (resources.displayMetrics.widthPixels * 0.45f).toInt())
        val left = buildLeft().also { it.translationX = -pw.toFloat() }
        val right = buildRight().also { it.translationX = pw.toFloat() }
        leftWin = left
        rightWin = right
        wm.addView(left, lp(pw, MATCH, Gravity.TOP or Gravity.START, true))
        wm.addView(right, lp(pw, MATCH, Gravity.TOP or Gravity.END, true))

        val status = buildStatus()
        statusWin = status
        wm.addView(status, lp(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, true).apply { y = dp(16) })

        val home = buildHome()
        homeWin = home
        wm.addView(home, lp(dp(40), dp(40), Gravity.TOP or Gravity.START, true).apply { x = dp(16); y = dp(16) })

        left.animate().translationX(0f).setDuration(280).setInterpolator(DecelerateInterpolator()).start()
        right.animate().translationX(0f).setDuration(280).setInterpolator(DecelerateInterpolator()).start()

        refreshStats()
        refreshRecent()
        ui.postDelayed(tick, 1000)
    }

    private fun closeTools() {
        if (!toolsOpen) return
        toolsOpen = false
        ui.removeCallbacks(tick)
        val l = leftWin
        val r = rightWin
        val s = statusWin
        val h = homeWin
        leftWin = null; rightWin = null; statusWin = null; homeWin = null
        l?.let { it.animate().translationX(-it.width.toFloat()).setDuration(220).start() }
        r?.let { it.animate().translationX(it.width.toFloat()).setDuration(220).start() }
        ui.postDelayed({
            listOf(l, r, s, h).forEach { v -> v?.let { safeRemove(it) } }
            handleLeft?.visibility = View.VISIBLE
            handleRight?.visibility = View.VISIBLE
        }, 240)
    }

    // ------------------------------------------------------------------ small ui helpers

    private fun wrapTop(topDp: Int) =
        LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(topDp) }

    private fun tag(t: String) = label(t, 10f, Theme.textFaint, bold = true).apply { letterSpacing = 0.2f }

    private fun chip(txt: String, onTap: () -> Unit): TextView =
        label(txt, 11f, Theme.textPrimary, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(8), dp(11), dp(8))
            background = roundBg(Theme.bgRaised, 9f, Theme.lineBright, 1)
            setOnClickListener { onTap() }
        }

    private fun styleChip(v: TextView, selected: Boolean) {
        v.background = if (selected) roundBg(Theme.ember, 9f) else roundBg(Theme.bgRaised, 9f, Theme.lineBright, 1)
        v.setTextColor(if (selected) Theme.onEmber else Theme.textPrimary)
    }

    private fun seek(max: Int, progress: Int, onChange: (Int) -> Unit): SeekBar =
        SeekBar(this).apply {
            this.max = max
            this.progress = progress
            progressTintList = ColorStateList.valueOf(Theme.ember)
            thumbTintList = ColorStateList.valueOf(Theme.ember)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }

    private fun tile(glyph: String, caption: String, onTap: () -> Unit): Tile {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(10), dp(6), dp(10))
            setOnClickListener { onTap() }
        }
        root.addView(label(glyph, 18f, Theme.textPrimary))
        val cap = label(caption, 10f, Theme.textDim, bold = true).apply { gravity = Gravity.CENTER }
        root.addView(cap, wrapTop(5))
        return Tile(root, cap).also { setTileOn(it, false) }
    }

    private fun setTileOn(t: Tile, on: Boolean) {
        t.root.background = if (on) roundBg(Theme.ember, 12f) else roundBg(Theme.bgRaised, 12f, Theme.line, 1)
        t.cap.setTextColor(if (on) Theme.onEmber else Theme.textDim)
    }

    private fun row2(a: Tile, b: Tile): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a.root, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(4) })
        addView(b.root, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(4) })
    }

    private fun launch(intent: Intent) {
        closeTools()
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) { /* no handler on this device */ }
    }

    // ------------------------------------------------------------------ left panel

    private fun buildLeft(): ChevronLayout {
        val panel = ChevronLayout(this, true)
        panel.setPadding(dp(18), dp(64), dp(40), dp(16)) // top padding leaves room for the home button
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        }
        panel.addView(scroll, LinearLayout.LayoutParams(MATCH, MATCH))

        col.addView(tag("PERFORMANCE"))
        val g = GaugeView(this, "CPU", "GHz")
        cpuGauge = g
        col.addView(g, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })

        tempText = label("Temp --  ·  Battery --", 12f, Theme.textPrimary, mono = true)
        col.addView(tempText, wrapTop(6))
        thermalText = label("Thermal: --", 11f, Theme.textDim, mono = true)
        col.addView(thermalText, wrapTop(4))

        col.addView(tag("VOLUME"), wrapTop(16))
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        col.addView(
            seek(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), am.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
            },
            wrapTop(4)
        )

        col.addView(tag("SHORTCUTS"), wrapTop(16))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(chip("Display") { launch(Intent(Settings.ACTION_DISPLAY_SETTINGS)) })
        row.addView(
            chip("Battery") { launch(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(8) }
        )
        col.addView(row, wrapTop(8))
        return panel
    }

    // ------------------------------------------------------------------ right panel

    private fun buildRight(): ChevronLayout {
        val panel = ChevronLayout(this, false)
        panel.setPadding(dp(40), dp(16), dp(18), dp(16))
        val frame = FrameLayout(this)
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(frame, ViewGroup.LayoutParams(MATCH, WRAP))
        }
        panel.addView(scroll, LinearLayout.LayoutParams(MATCH, MATCH))

        rightPages = listOf(buildMainPage(), buildCrossPage(), buildFilterPage())
        rightPages.forEach { frame.addView(it, FrameLayout.LayoutParams(MATCH, WRAP)) }
        showPage(0)
        return panel
    }

    private fun showPage(i: Int) {
        rightPages.forEachIndexed { idx, v -> v.visibility = if (idx == i) View.VISIBLE else View.GONE }
    }

    private fun buildMainPage(): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tag("SYSTEM"))
        val g = GaugeView(this, "GPU", "MHz")
        gpuGauge = g
        col.addView(g, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })

        val wifi = tile("📶", "Wi-Fi") {
            launch(
                if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI)
                else Intent(Settings.ACTION_WIFI_SETTINGS)
            )
        }
        val bt = tile("🔷", "Bluetooth") { launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        val cross = tile("🎯", "Crosshair") { showPage(1) }
        val filters = tile("🎨", "Filters") { showPage(2) }
        wifiTile = wifi
        btTile = bt
        col.addView(row2(wifi, bt), wrapTop(10))
        col.addView(row2(cross, filters), wrapTop(8))

        col.addView(tag("RECENT APPS"), wrapTop(16))
        recentBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(recentBox, wrapTop(6))
        return col
    }

    private fun pageHeader(title: String): LinearLayout {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        h.addView(chip("‹ Back") { showPage(0) })
        h.addView(
            label(title, 15f, Theme.textPrimary, bold = true),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(12) }
        )
        return h
    }

    // ---- crosshair settings page

    private fun buildCrossPage(): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(pageHeader("Crosshair"))

        val previewBox = FrameLayout(this).apply { background = roundBg(Theme.bgDeep, 12f, Theme.line, 1) }
        val preview = CrosshairView(this)
        crossPreview = preview
        previewBox.addView(preview, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(previewBox, LinearLayout.LayoutParams(MATCH, dp(96)).apply { topMargin = dp(12) })

        val toggle = chip("") { crossOn = !crossOn; applyCrosshair(); syncCrossUi() }
        crossToggle = toggle
        col.addView(toggle, wrapTop(10))

        col.addView(tag("STYLE"), wrapTop(14))
        val styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        styleChips.clear()
        CrosshairView.Style.values().forEachIndexed { i, st ->
            val c = chip(st.title) { crossStyle = st; applyCrosshair(); syncCrossUi() }
            styleChips.add(st to c)
            styleRow.addView(c, LinearLayout.LayoutParams(WRAP, WRAP).apply { if (i > 0) leftMargin = dp(6) })
        }
        col.addView(styleRow, wrapTop(6))

        col.addView(tag("COLOUR"), wrapTop(14))
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        colorDots.clear()
        val colors = listOf(
            Theme.ember, Theme.amber, Theme.good,
            Color.parseColor("#3FA8FF"), Color.WHITE, Color.parseColor("#FF5CD6")
        )
        colors.forEachIndexed { i, c ->
            val dot = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(dp(2), Color.TRANSPARENT) }
                setOnClickListener { crossColor = c; applyCrosshair(); syncCrossUi() }
            }
            colorDots.add(c to dot)
            colorRow.addView(dot, LinearLayout.LayoutParams(dp(26), dp(26)).apply { if (i > 0) leftMargin = dp(8) })
        }
        col.addView(colorRow, wrapTop(6))

        sizeLabel = label("", 11f, Theme.textDim, mono = true)
        col.addView(sizeLabel, wrapTop(14))
        col.addView(seek(38, crossSize - 14) { crossSize = it + 14; applyCrosshair(); syncCrossUi() }, wrapTop(2))

        opacityLabel = label("", 11f, Theme.textDim, mono = true)
        col.addView(opacityLabel, wrapTop(10))
        col.addView(seek(70, crossOpacity - 30) { crossOpacity = it + 30; applyCrosshair(); syncCrossUi() }, wrapTop(2))

        syncCrossUi()
        return col
    }

    private fun syncCrossUi() {
        crossPreview?.let { it.style = crossStyle; it.color = crossColor; it.sizeDp = crossSize; it.opacityPct = crossOpacity }
        styleChips.forEach { (st, c) -> styleChip(c, st == crossStyle) }
        colorDots.forEach { (c, v) ->
            (v.background as GradientDrawable).setStroke(dp(2), if (c == crossColor) Color.WHITE else Color.TRANSPARENT)
        }
        crossToggle?.let {
            it.text = if (crossOn) "Crosshair in game: ON" else "Crosshair in game: OFF"
            styleChip(it, crossOn)
        }
        sizeLabel?.text = "Size  ${crossSize}px"
        opacityLabel?.text = "Opacity  ${crossOpacity}%"
    }

    /** Shows/hides the touch-through crosshair window in the middle of the screen. */
    private fun applyCrosshair() {
        if (crossOn) {
            val v = crossWin ?: CrosshairView(this).also {
                crossWin = it
                wm.addView(it, lp(dp(140), dp(140), Gravity.CENTER, false))
            }
            v.style = crossStyle; v.color = crossColor; v.sizeDp = crossSize; v.opacityPct = crossOpacity
        } else {
            crossWin?.let { safeRemove(it) }
            crossWin = null
        }
    }

    // ---- filter page

    private fun buildFilterPage(): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(pageHeader("Filters"))
        col.addView(
            label("Colour tint drawn over the screen. Works on any app, no root needed.", 10f, Theme.textFaint),
            wrapTop(8)
        )
        presetRows.clear()
        presets.forEachIndexed { i, p ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener { presetIndex = i; applyFilter(); syncFilterUi() }
            }
            val swatch = View(this).apply {
                background = roundBg(if (p.tint == 0) Theme.bgRaised else (p.tint or 0xFF000000.toInt()), 8f, Theme.lineBright, 1)
            }
            r.addView(swatch, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(10) })
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label(p.name, 12f, Theme.textPrimary, bold = true))
            texts.addView(label(p.info, 9f, Theme.textFaint, mono = true), wrapTop(2))
            r.addView(texts)
            presetRows.add(r)
            col.addView(r, wrapTop(7))
        }
        syncFilterUi()
        return col
    }

    private fun syncFilterUi() {
        presetRows.forEachIndexed { i, r ->
            r.background = if (i == presetIndex) roundBg(Theme.bgRaised, 11f, Theme.ember, 1)
            else roundBg(Theme.bgRaised, 11f, Theme.line, 1)
        }
    }

    /** Draws (or removes) a full-screen, touch-through tint window. */
    private fun applyFilter() {
        val tint = presets[presetIndex].tint
        if (tint == 0) {
            filterWin?.let { safeRemove(it) }
            filterWin = null
            return
        }
        val v = filterWin ?: View(this).also {
            filterWin = it
            wm.addView(it, lp(MATCH, MATCH, Gravity.TOP or Gravity.START, false))
        }
        v.setBackgroundColor(tint)
    }

    // ------------------------------------------------------------------ status pill + home

    private fun buildStatus(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            background = roundBg(0xE609080C.toInt(), 24f, Theme.line, 1)
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { cycleDemo() } // tap the pill to preview the green / amber / red states
        }
        val dotBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Theme.good) }
        statusDotBg = dotBg
        pill.addView(View(this).apply { background = dotBg }, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(7) })
        statusLabel = label("Optimal", 12f, Theme.good, mono = true, bold = true)
        pill.addView(statusLabel)
        bar.addView(pill)

        statusReadout = label("", 11f, Theme.textDim, mono = true)
        bar.addView(statusReadout, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(12); rightMargin = dp(12) })
        bar.addView(chip("Close ✕") { closeTools() })
        return bar
    }

    private fun buildHome(): View = label("🏠", 18f, Theme.textPrimary).apply {
        gravity = Gravity.CENTER
        background = roundBg(Theme.bgPanel, 11f, Theme.lineBright, 1)
        setOnClickListener { closeTools() }
    }

    private fun cycleDemo() {
        demoState = when (demoState) {
            null -> PerfState.GOOD
            PerfState.GOOD -> PerfState.WARN
            PerfState.WARN -> PerfState.HOT
            PerfState.HOT -> null
        }
        refreshStats()
    }

    // ------------------------------------------------------------------ live data

    private fun refreshStats() {
        val snap = SystemStats.read(this)
        val demo = demoState
        val state = demo ?: snap.state

        val cpuText: String
        val cpuF: Float
        val gpuText: String
        val gpuF: Float
        val tempStr: String
        if (demo != null) {
            val d = demos.getValue(demo)
            cpuText = d.cpu; cpuF = d.cpuF; gpuText = d.gpu; gpuF = d.gpuF; tempStr = d.temp
        } else {
            cpuText = snap.cpuGHz?.let { "%.2f".format(it) } ?: "N/A"
            cpuF = snap.cpuFraction
            gpuText = snap.gpuMHz?.toString() ?: "N/A"
            gpuF = snap.gpuFraction
            tempStr = "%.0f°C".format(snap.tempC)
        }

        cpuGauge?.let { it.arcColor = state.color; it.valueText = cpuText; it.fraction = cpuF }
        gpuGauge?.let { it.arcColor = state.color; it.valueText = gpuText; it.fraction = gpuF }
        tempText?.text = "Temp $tempStr  ·  Battery ${snap.batteryPct}%"
        thermalText?.text = "Thermal: ${snap.thermalText}"
        statusDotBg?.setColor(state.color)
        statusLabel?.let { it.text = state.label + if (demo != null) " · demo" else ""; it.setTextColor(state.color) }
        statusReadout?.text = "$tempStr · ${snap.batteryPct}%"

        wifiTile?.let { setTileOn(it, isWifiOn()) }
        btTile?.let { setTileOn(it, isBluetoothOn()) }
    }

    private fun isWifiOn(): Boolean = try {
        (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: Exception) { false }

    private fun isBluetoothOn(): Boolean = try {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
    } catch (e: Exception) { false }

    private fun refreshRecent() {
        val box = recentBox ?: return
        box.removeAllViews()
        if (!RecentApps.hasAccess(this)) {
            box.addView(
                label("Grant Usage access in the PulseGrid app to see recent apps.", 10f, Theme.textFaint)
            )
            return
        }
        val items = RecentApps.load(this, 4)
        if (items.isEmpty()) {
            box.addView(label("No recent apps yet.", 10f, Theme.textFaint))
            return
        }
        items.forEach { item ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(7), dp(8), dp(7))
                background = roundBg(Theme.bgRaised, 10f, Theme.line, 1)
                setOnClickListener {
                    packageManager.getLaunchIntentForPackage(item.pkg)?.let { launch(it) }
                }
            }
            r.addView(ImageView(this).apply { setImageDrawable(item.icon) }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(9) })
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            t.addView(label(item.label, 11f, Theme.textPrimary, bold = true).apply { maxLines = 1 })
            t.addView(label(RecentApps.ago(item.lastUsed), 9f, Theme.textFaint, mono = true), wrapTop(2))
            r.addView(t)
            box.addView(r, wrapTop(6))
        }
    }
}
