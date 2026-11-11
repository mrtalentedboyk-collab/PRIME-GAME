package com.primegame.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

class CaptureOverlayService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        private const val CHANNEL = "prime_capture_channel"
        private const val NOTIFICATION_ID = 781
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: View? = null
    private var windowManager: WindowManager? = null
    private val running = AtomicBoolean(false)
    private var frameCount = 0L
    private var lastFrameAt = 0L
    private var captureWidth = 720
    private var captureHeight = 1280
    private val handler = Handler(Looper.getMainLooper())
    private var statusText: TextView? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Preparing screen capture"))
        if (intent == null || !intent.hasExtra(EXTRA_RESULT_DATA)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startCapture(intent)
        showOverlay()
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(message: String): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = PendingIntent.getActivity(this, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("PRIME GAME is active")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun startCapture(intent: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") (intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent)
        if (data == null) {
            stopSelf()
            return
        }
        projection = manager.getMediaProjection(resultCode, data)
        val metrics = resources.displayMetrics
        captureWidth = metrics.widthPixels.coerceAtLeast(1)
        captureHeight = metrics.heightPixels.coerceAtLeast(1)
        reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage()
            if (image != null) {
                frameCount++
                lastFrameAt = System.currentTimeMillis()
                image.close()
                handler.post {
                    statusText?.text = "SCREEN CAPTURE ACTIVE\nFrames received: $frameCount\nBoard analysis: not available in this build"
                }
            }
        }, handler)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, handler)
        virtualDisplay = projection?.createVirtualDisplay(
            "PrimeGameCapture", captureWidth, captureHeight, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler
        )
        running.set(true)
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            setBackgroundColor(Color.argb(232, 13, 12, 20))
        }
        val header = TextView(this).apply {
            text = "PRIME GAME  ×"
            textSize = 14f
            setTextColor(Color.rgb(255, 215, 0))
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { stopSelf() }
        }
        statusText = TextView(this).apply {
            text = "SCREEN CAPTURE ACTIVE\nWaiting for frames…\nLive prediction not enabled"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 8)
        }
        val aim = TextView(this).apply {
            text = "AIM PREDICTION: NOT AVAILABLE"
            textSize = 10f
            setTextColor(Color.rgb(255, 215, 0))
            setPadding(0, 8, 0, 4)
        }
        val hint = TextView(this).apply {
            text = "Tap PRIME GAME × to close HUD"
            textSize = 9f
            setTextColor(Color.LTGRAY)
        }
        panel.addView(header); panel.addView(statusText); panel.addView(aim); panel.addView(hint)
        overlay = panel
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                   else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12; y = 160
        }
        panel.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f; private var downY = 0f
            private var initialX = 0; private var initialY = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        initialX = params.x; initialY = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = (initialX - (event.rawX - downX)).toInt()
                        params.y = (initialY + (event.rawY - downY)).toInt()
                        windowManager?.updateViewLayout(panel, params)
                        return true
                    }
                }
                return false
            }
        })
        try { windowManager?.addView(panel, params) } catch (_: Exception) { stopSelf() }
    }

    override fun onDestroy() {
        running.set(false)
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        virtualDisplay?.release()
        reader?.close()
        projection?.stop()
        overlay = null
        super.onDestroy()
    }
}
