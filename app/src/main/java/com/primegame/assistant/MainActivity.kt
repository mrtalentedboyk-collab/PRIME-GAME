package com.primegame.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#0D131A"))
        }

        val logoImg = ImageView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(300, 300).apply { setMargins(0, 0, 0, 50) }
        }

        val prefs = getSharedPreferences("PrimeGamePrefs", Context.MODE_PRIVATE)
        var selectedGame = prefs.getString("selected_game", "None")

        fun updateLogo() {
            if (selectedGame == "Carrom Pool") logoImg.setImageResource(android.R.drawable.ic_menu_gallery) 
            else logoImg.setImageResource(android.R.drawable.ic_menu_help)
        }
        updateLogo()

        val selectGameBtn = Button(this).apply {
            text = "Selected: $selectedGame (Click to Change)"
            setOnClickListener {
                val games = arrayOf("Carrom Pool", "Carrom King")
                AlertDialog.Builder(this@MainActivity).setTitle("Select Game")
                    .setItems(games) { _, which ->
                        selectedGame = games[which]
                        prefs.edit().putString("selected_game", selectedGame).apply()
                        text = "Selected: $selectedGame (Click to Change)"
                        updateLogo()
                    }.show()
            }
        }

        val startBtn = Button(this).apply {
            text = "START ASSISTANT"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    Toast.makeText(this@MainActivity, "Allow Display Overlays", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                startActivityForResult(projectionManager.createScreenCaptureIntent(), 1000)
            }
        }

        layout.addView(logoImg); layout.addView(selectGameBtn); layout.addView(startBtn)
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1000 && resultCode == RESULT_OK) {
            val intent = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra("RESULT_CODE", resultCode)
                putExtra("RESULT_DATA", data)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)

            val prefs = getSharedPreferences("PrimeGamePrefs", Context.MODE_PRIVATE)
            val gamePkg = if (prefs.getString("selected_game", "") == "Carrom Pool") "com.miniclip.carrom" else ""
            if (gamePkg.isNotEmpty()) {
                val launchIntent = packageManager.getLaunchIntentForPackage(gamePkg)
                if (launchIntent != null) startActivity(launchIntent)
                else Toast.makeText(this, "Game not installed", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
