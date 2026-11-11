package com.primegame.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    private lateinit var projectionManager: MediaProjectionManager
    private var selectedPackageName: String? = null
    private var selectedAppName: String = "No Game Selected"
    private var selectedAppIcon: Drawable? = null

    private lateinit var gameIconView: ImageView
    private lateinit var gameNameView: TextView
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0B0E14"))
            setPadding(50, 80, 50, 50)
        }

        val title = TextView(this).apply {
            text = "PRIME GAME"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        }
        val subTitle = TextView(this).apply {
            text = "PRO AIM ASSISTANT"
            setTextColor(Color.LTGRAY)
            textSize = 14f
            setPadding(0, 0, 0, 80)
        }

        gameIconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200)
            setImageResource(android.R.drawable.sym_def_app_icon)
        }

        gameNameView = TextView(this).apply {
            text = selectedAppName
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 20, 0, 60)
        }

        val btnChoose = Button(this).apply {
            text = "CHOOSE GAME"
            setBackgroundColor(Color.parseColor("#1F2937"))
            setTextColor(Color.WHITE)
            setOnClickListener { showAppChooserDialog() }
        }

        btnStart = Button(this).apply {
            text = "START PRIME HUD"
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setTextColor(Color.BLACK)
            isEnabled = false
            setOnClickListener { checkOverlayPermission() }
        }

        layout.addView(title)
        layout.addView(subTitle)
        layout.addView(gameIconView)
        layout.addView(gameNameView)
        layout.addView(btnChoose)
        layout.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, 40) })
        layout.addView(btnStart)

        setContentView(layout)
    }

    private fun showAppChooserDialog() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        
        val appNames = resolveInfos.map { it.loadLabel(pm).toString() }.toTypedArray()
        val appPackages = resolveInfos.map { it.activityInfo.packageName }.toTypedArray()

        AlertDialog.Builder(this).apply {
            setTitle("Select Game")
            setItems(appNames) { _, which ->
                selectedPackageName = appPackages[which]
                selectedAppName = appNames[which]
                selectedAppIcon = resolveInfos[which].loadIcon(pm)
                
                gameNameView.text = selectedAppName
                gameIconView.setImageDrawable(selectedAppIcon)
                btnStart.isEnabled = true
                btnStart.text = "START & LAUNCH GAME"
            }
            show()
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 1001)
        } else {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), 1002)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1002 && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra("RESULT_CODE", resultCode)
                putExtra("RESULT_DATA", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
            
            selectedPackageName?.let { pkg ->
                packageManager.getLaunchIntentForPackage(pkg)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                }
            }
        }
    }
}
