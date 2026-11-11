package com.primegame.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color

class MainActivity : Activity() {
    private val captureRequest = 4102
    private lateinit var status: TextView
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        render()
    }

    private fun render() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 38, 28, 28)
            setBackgroundColor(Color.rgb(9, 7, 17))
        }
        scroll.addView(root)
        fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView {
            return TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 9, 0, 9)
            }
        }
        root.addView(text("PRIME GAME", 26f, Color.rgb(255, 215, 0), true))
        root.addView(text("LIVE SCREEN COMPANION", 11f, Color.LTGRAY, true))
        root.addView(text(
            "This native build can request Android screen capture and display a floating control panel. Live board recognition and reliable multi-coin physics prediction are not implemented in this build.",
            14f, Color.rgb(205, 199, 214)
        ))
        status = text("STATUS: READY", 13f, Color.rgb(255, 215, 0), true)
        root.addView(status)

        val overlay = makeButton("GRANT DISPLAY OVER OTHER APPS") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else status.text = "STATUS: OVERLAY PERMISSION GRANTED"
        }
        root.addView(overlay)

        root.addView(makeButton("START SCREEN CAPTURE & FLOATING HUD") {
            if (!Settings.canDrawOverlays(this)) {
                status.text = "Grant overlay permission first."
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else {
                startActivityForResult(projectionManager.createScreenCaptureIntent(), captureRequest)
            }
        })
        root.addView(makeButton("LAUNCH CARROM POOL") {
            val launch = packageManager.getLaunchIntentForPackage("com.miniclip.carrompool")
            if (launch != null) startActivity(launch)
            else status.text = "Carrom Pool is not installed."
        })
        root.addView(makeButton("STOP PRIME GAME HUD") {
            stopService(Intent(this, CaptureOverlayService::class.java))
            status.text = "STATUS: STOPPED"
        })
        root.addView(text(
            "Important: Android displays a screen-capture consent prompt. Capture can be stopped from the Android system privacy indicator or the PRIME GAME notification. The overlay does not inject touches or alter the game.",
            12f, Color.rgb(160, 153, 171)
        ))
        setContentView(scroll)
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(255, 215, 0))
            setPadding(12, 8, 12, 8)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 12, 0, 4)
            layoutParams = lp
            setOnClickListener { action() }
        }
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra(CaptureOverlayService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureOverlayService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service)
            else startService(service)
            status.text = "STATUS: CAPTURE START REQUESTED"
        } else if (requestCode == captureRequest) {
            status.text = "STATUS: SCREEN CAPTURE CANCELLED"
        }
    }
}
