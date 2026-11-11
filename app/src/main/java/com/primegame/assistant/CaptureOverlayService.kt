package com.primegame.assistant

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Switch
import android.widget.TextView

class CaptureOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var sharedPreferences: SharedPreferences

    private var hudView: View? = null
    private var predictionCanvas: PredictionOverlayView? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val carromEngine = CarromEngine()

    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 420
    private var isProcessingFrame = false

    companion object {
        const val CHANNEL_ID = "PrimeGameOverlayChannel"
        const val NOTIFICATION_ID = 2001
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_RESULT_DATA = "RESULT_DATA"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences("PrimeGamePrefs", Context.MODE_PRIVATE)

        val metrics = DisplayMetrics()
        val defaultDisplay = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(0)
        defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        startForegroundServiceNotification()
        setupPredictionCanvas()
        setupFloatingHud()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == Activity.RESULT_OK && resultData != null && mediaProjection == null) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            startScreenCapture()
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PRIME GAME HUD",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("PRIME GAME Active")
            .setContentText("Aim Prediction Engine Running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupPredictionCanvas() {
        predictionCanvas = PredictionOverlayView(this).apply {
            isAimPredictionEnabled = sharedPreferences.getBoolean("aim_pred", true)
            isCollisionLinesEnabled = sharedPreferences.getBoolean("coin_col", true)
            isBankShotEnabled = sharedPreferences.getBoolean("bank_shot", true)
            isPowerGuideEnabled = sharedPreferences.getBoolean("power_guide", true)
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(predictionCanvas, canvasParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingHud() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val hudParams = WindowManager.LayoutParams(
            (screenWidth * 0.82f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 120
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60D131A"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#00E5FF"))
            }
        }

        val header = RelativeLayout(this).apply {
            val title = TextView(context).apply {
                text = "PRIME GAME • DRAG TO MOVE"
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
            }
            val closeBtn = TextView(context).apply {
                text = "CLOSE HUD ×"
                setTextColor(Color.LTGRAY)
                textSize = 11f
                setOnClickListener { stopSelf() }
            }
            val closeParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_END) }

            addView(title)
            addView(closeBtn, closeParams)
        }
        root.addView(header)

        fun addFeatureSwitch(label: String, prefKey: String, defaultValue: Boolean, onToggle: (Boolean) -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val txt = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val switchView = Switch(this).apply {
                isChecked = sharedPreferences.getBoolean(prefKey, defaultValue)
                setOnCheckedChangeListener { _, isChecked ->
                    sharedPreferences.edit().putBoolean(prefKey, isChecked).apply()
                    onToggle(isChecked)
                }
            }
            row.addView(txt)
            row.addView(switchView)
            root.addView(row)
        }

        addFeatureSwitch("Aim prediction", "aim_pred", true) {
            predictionCanvas?.isAimPredictionEnabled = it
            if (!it) predictionCanvas?.clearPredictions()
        }
        addFeatureSwitch("Auto aim assist", "auto_aim", false) { }
        addFeatureSwitch("Coin collision lines", "coin_col", true) {
            predictionCanvas?.isCollisionLinesEnabled = it
            predictionCanvas?.postInvalidate()
        }
        addFeatureSwitch("Bank shot guide", "bank_shot", true) {
            predictionCanvas?.isBankShotEnabled = it
            predictionCanvas?.postInvalidate()
        }
        addFeatureSwitch("Power guide", "power_guide", true) {
            predictionCanvas?.isPowerGuideEnabled = it
            predictionCanvas?.postInvalidate()
        }

        root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = hudParams.x
                        initialY = hudParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        hudParams.x = initialX + (event.rawX - touchX).toInt()
                        hudParams.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(root, hudParams)
                        return true
                    }
                }
                return false
            }
        })

        hudView = root
        windowManager.addView(hudView, hudParams)
    }

    @SuppressLint("WrongConstant")
    private fun startScreenCapture() {
        val captureWidth = screenWidth / 2
        val captureHeight = screenHeight / 2

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "CarromStream",
            captureWidth,
            captureHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (isProcessingFrame || predictionCanvas?.isAimPredictionEnabled != true) {
                image.close()
                return@setOnImageAvailableListener
            }

            isProcessingFrame = true
            Thread {
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * captureWidth

                    val bitmap = Bitmap.createBitmap(
                        captureWidth + rowPadding / pixelStride,
                        captureHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val result = carromEngine.processFrame(bitmap, screenWidth, screenHeight)
                    bitmap.recycle()

                    if (result != null) {
                        predictionCanvas?.updatePredictions(
                            result.strikerTrajectory,
                            result.coinTrajectory,
                            result.collisionPoint,
                            result.targetPocket
                        )
                    } else {
                        predictionCanvas?.clearPredictions()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isProcessingFrame = false
                }
            }.start()
        }, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        hudView?.let { windowManager.removeView(it) }
        predictionCanvas?.let { windowManager.removeView(it) }
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}
