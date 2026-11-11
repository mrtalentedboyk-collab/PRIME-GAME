package com.primegame.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAndRequestMediaProjection()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra(CaptureOverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureOverlayService.EXTRA_RESULT_DATA, result.data)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#0B0E14"))
            setPadding(60, 60, 60, 60)
        }

        val title = android.widget.TextView(this).apply {
            text = "PRIME GAME ASSISTANT"
            setTextColor(android.graphics.Color.parseColor("#00E5FF"))
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 80)
        }

        val btnStart = Button(this).apply {
            text = "START PRIME HUD"
            setBackgroundColor(android.graphics.Color.parseColor("#00E5FF"))
            setTextColor(android.graphics.Color.BLACK)
            setOnClickListener { checkOverlayPermission() }
        }

        val btnLaunchGame = Button(this).apply {
            text = "LAUNCH CARROM POOL"
            setBackgroundColor(android.graphics.Color.parseColor("#1F2937"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { launchCarromPool() }
        }

        layout.addView(title)
        layout.addView(btnStart)
        layout.addView(android.widget.Space(this).apply { minimumHeight = 40 })
        layout.addView(btnLaunchGame)
        setContentView(layout)
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            checkAndRequestMediaProjection()
        }
    }

    private fun checkAndRequestMediaProjection() {
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
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
