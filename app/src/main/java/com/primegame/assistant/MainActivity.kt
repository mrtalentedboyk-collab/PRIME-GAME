package com.primegame.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var projectionManager: MediaProjectionManager

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val REQUEST_MEDIA_PROJECTION = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0E14"))
            setPadding(60, 60, 60, 60)
        }

        val title = TextView(this).apply {
            text = "PRIME GAME ASSISTANT"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
        }

        val btnStart = Button(this).apply {
            text = "START PRIME HUD"
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setTextColor(Color.BLACK)
            setOnClickListener { checkOverlayPermission() }
        }

        val btnLaunchGame = Button(this).apply {
            text = "LAUNCH CARROM POOL"
            setBackgroundColor(Color.parseColor("#1F2937"))
            setTextColor(Color.WHITE)
            setOnClickListener { launchCarromPool() }
        }

        layout.addView(title)
        layout.addView(btnStart)
        layout.addView(Space(this).apply { minimumHeight = 40 })
        layout.addView(btnLaunchGame)
        setContentView(layout)
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        } else {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        val captureIntent = projectionManager.createScreenCaptureIntent()
        @Suppress("DEPRECATION")
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_OVERLAY_PERMISSION -> {
                if (Settings.canDrawOverlays(this)) {
                    requestMediaProjection()
                } else {
                    Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_MEDIA_PROJECTION -> {
                if (resultCode == RESULT_OK && data != null) {
                    val serviceIntent = Intent(this, CaptureOverlayService::class.java).apply {
                        putExtra(CaptureOverlayService.EXTRA_RESULT_CODE, resultCode)
                        putExtra(CaptureOverlayService.EXTRA_RESULT_DATA, data)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    Toast.makeText(this, "PRIME GAME HUD Started!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Screen capture permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun launchCarromPool() {
        val packageNames = listOf("com.miniclip.carrompool", "com.miniclip.carrom")
        var launchIntent: Intent? = null

        for (pkg in packageNames) {
            launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) break
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "Carrom Pool is not installed!", Toast.LENGTH_LONG).show()
        }
    }
}
