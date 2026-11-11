package com.primegame.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import android.graphics.Color

class MainActivity : Activity() {
    private val captureRequest = 4102
    private lateinit var status: TextView
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 38, 28, 28)
            setBackgroundColor(Color.rgb(9, 7, 17))
        }

        val scroll = ScrollView(this)
        scroll.addView(root)

        fun addText(value: String, size: Float, color: Int) {
            root.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                setPadding(0, 10, 0, 10)
            })
        }

        addText("PRIME GAME", 28f, Color.rgb(180, 65, 255))
        addText("CARROM AIM COMPANION", 15f, Color.WHITE)

        status = TextView(this).apply {
            text = "STATUS: READY"
            textSize = 14f
            setTextColor(Color.YELLOW)
        }
        root.addView(status)

        fun addButton(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            })
        }

        addButton("GRANT DISPLAY OVER OTHER APPS") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                status.text = "OVERLAY PERMISSION GRANTED"
            }
        }

        addButton("START SCREEN CAPTURE & FLOATING HUD") {
            if (!Settings.canDrawOverlays(this)) {
                status.text = "GRANT OVERLAY PERMISSION FIRST"
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                startActivityForResult(
                    projectionManager.createScreenCaptureIntent(),
                    captureRequest
                )
            }
        }

        addButton("LAUNCH CARROM POOL") {
            val gamePackage = "com.miniclip.carrom"
            val launch = packageManager.getLaunchIntentForPackage(gamePackage)

            if (launch != null) {
                startActivity(launch)
            } else {
                status.text = "GAME NOT FOUND - INSTALL CARROM POOL"
            }
        }

        addButton("STOP PRIME GAME HUD") {
            stopService(Intent(this, CaptureOverlayService::class.java))
            status.text = "STATUS: STOPPED"
        }

        addText(
            "Live coin detection and aim prediction are not implemented yet.",
            13f,
            Color.LTGRAY
        )

        setContentView(scroll)
    }

    @Deprecated("Deprecated API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == captureRequest &&
            resultCode == RESULT_OK && data != null
        ) {
            val service = Intent(
                this,
                CaptureOverlayService::class.java
            ).apply {
                putExtra(
                    CaptureOverlayService.EXTRA_RESULT_CODE,
                    resultCode
                )
                putExtra(
                    CaptureOverlayService.EXTRA_RESULT_DATA,
                    data
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service)
            } else {
                startService(service)
            }

            status.text = "SCREEN CAPTURE STARTED"
        } else {
            status.text = "SCREEN CAPTURE CANCELLED"
        }
    }
}
