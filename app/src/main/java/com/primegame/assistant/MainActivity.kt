package com.primegame.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private val captureRequest = 4102
    private lateinit var status: TextView
    private lateinit var projectionManager: MediaProjectionManager
    private val bg = Color.rgb(9, 13, 24)
    private val card = Color.rgb(20, 28, 44)
    private val accent = Color.rgb(40, 220, 190)
    private val prefs by lazy { getSharedPreferences("prime_settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        render()
    }

    private fun shape(color: Int, radius: Float = 22f): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
    }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun render() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 34, 22, 30) }
        scroll.addView(root)
        root.addView(label("PRIME GAME", 29f, Color.WHITE, true))
        root.addView(label("CARROM COMPANION  /  CONTROL CENTER", 10f, accent, true).apply { letterSpacing = .14f })
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20); background = shape(card, 24f)
        }
        val badge = label("●  READY TO CONNECT", 11f, accent, true)
        hero.addView(badge)
        hero.addView(label("Your game,\nyour control.", 27f, Color.WHITE, true).apply { setPadding(0, 12, 0, 8) })
        hero.addView(label("Start screen sharing to open the floating PRIME GAME panel over Carrom Pool.", 13f, Color.rgb(177, 190, 207)))
        root.addView(hero, margin(0, 24, 0, 16))
        root.addView(label("QUICK ACTIONS", 11f, Color.LTGRAY, true))
        root.addView(action("01", "Display over other apps", "Allow the floating control panel", "GRANT PERMISSION") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else status.text = "Overlay permission is already enabled."
        })
        root.addView(action("02", "Start floating HUD", "Share your screen with Android's secure prompt", "START CAPTURE") {
            if (!Settings.canDrawOverlays(this)) {
                status.text = "Please grant overlay permission first."
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else startActivityForResult(projectionManager.createScreenCaptureIntent(), captureRequest)
        })
        root.addView(action("03", "Open Carrom Pool", "Switch to the installed game", "LAUNCH GAME") {
            val launch = packageManager.getLaunchIntentForPackage("com.miniclip.carrompool")
            if (launch != null) startActivity(launch) else status.text = "Carrom Pool was not found. Open it manually once if package differs."
        })
        root.addView(action("04", "Stop floating HUD", "Close PRIME GAME overlay and capture", "STOP HUD") {
            stopService(Intent(this, CaptureOverlayService::class.java)); status.text = "HUD stopped."
        })
        val statusCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 15, 16, 15); background = shape(Color.rgb(15, 42, 47), 18f) }
        statusCard.addView(label("SYSTEM STATUS", 10f, accent, true))
        status = label("Ready. Grant permission, then start capture.", 13f, Color.WHITE)
        status.setPadding(0, 7, 0, 0); statusCard.addView(status)
        root.addView(statusCard, margin(0, 14, 0, 0))
        root.addView(label("PREDICTION STATUS", 11f, Color.LTGRAY, true).apply { setPadding(2, 22, 0, 8) })
        root.addView(label("The floating switches control their display modes. Accurate live coin recognition and physics prediction still need device/gameplay validation; this build does not claim verified automatic aiming or shot execution.", 12f, Color.rgb(157, 171, 190)))
        root.addView(label("PRIME GAME  •  VERSION 1.1", 10f, Color.GRAY).apply { gravity = Gravity.CENTER; setPadding(0, 28, 0, 0) })
        setContentView(scroll)
    }

    private fun margin(l: Int, t: Int, r: Int, b: Int) = LinearLayout.LayoutParams(-1, -2).apply { setMargins(l, t, r, b) }
    private fun action(num: String, title: String, desc: String, button: String, click: () -> Unit): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 14, 16, 14); background = shape(card, 18f) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(num, 12f, accent, true), LinearLayout.LayoutParams(36, -2))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; weightSum = 1f }
        texts.addView(label(title, 15f, Color.WHITE, true)); texts.addView(label(desc, 11f, Color.LTGRAY))
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f)); box.addView(row)
        val btn = Button(this).apply { text = button; isAllCaps = false; textSize = 11f; setTextColor(Color.BLACK); background = shape(accent, 14f); setOnClickListener { click() } }
        box.addView(btn, margin(36, 10, 0, 0)); box.layoutParams = margin(0, 10, 0, 0); return box
    }

    @Deprecated("Deprecated API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra(CaptureOverlayService.EXTRA_RESULT_CODE, resultCode); putExtra(CaptureOverlayService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
            status.text = "Screen capture started. Floating HUD is opening."
        } else if (requestCode == captureRequest) status.text = "Screen capture permission cancelled."
    }
}
