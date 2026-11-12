package com.primegame.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#0D131A"))
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(this).apply {
            text = "PRIME GAME ASSISTANT"
            setTextColor(android.graphics.Color.parseColor("#00E5FF"))
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 50)
        }

        val prefs = getSharedPreferences("PrimeGamePrefs", Context.MODE_PRIVATE)
        var selectedGame = prefs.getString("selected_game", "Carrom Pool")

        val selectGameBtn = Button(this).apply {
            text = "Selected Game: $selectedGame"
            setOnClickListener {
                val games = arrayOf("Carrom Pool", "Carrom King")
                AlertDialog.Builder(this@MainActivity).setTitle("Select Game")
                    .setItems(games) { _, which ->
                        selectedGame = games[which]
                        prefs.edit().putString("selected_game", selectedGame).apply()
                        text = "Selected Game: $selectedGame"
                    }.show()
            }
        }

        val startBtn = Button(this).apply {
            text = "START ASSISTANT"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    Toast.makeText(this@MainActivity, "Please Allow Display Overlays", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                startActivityForResult(projectionManager.createScreenCaptureIntent(), 1000)
            }
        }

        layout.addView(title)
        layout.addView(selectGameBtn)
        layout.addView(startBtn)
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1000 && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, CaptureOverlayService::class.java).apply {
                putExtra("RESULT_CODE", resultCode)
                putExtra("RESULT_DATA", data)
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            val prefs = getSharedPreferences("PrimeGamePrefs", Context.MODE_PRIVATE)
            val gamePkg = if (prefs.getString("selected_game", "") == "Carrom Pool") "com.miniclip.carrom" else "com.moonfrog.carromking"
            
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(gamePkg)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this, "Assistant active in background!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Ignore
            }
            finish()
        }
    }
}
