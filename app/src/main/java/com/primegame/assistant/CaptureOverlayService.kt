package com.primegame.assistant

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import kotlin.math.abs

class CaptureOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var sharedPreferences: SharedPreferences
    private var hudRootView: View? = null
    private var predictionCanvas: PredictionOverlayView? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val carromEngine = CarromEngine()
    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 420
    @Volatile private var isProcessingFrame = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences("PrimeGamePrefs", Context.MODE_PRIVATE)
        
        val metrics = DisplayMetrics()
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.getDisplay(0)?.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        startForegroundServiceNotification()
        setupPredictionCanvas()
        setupFloatingHud()
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "PrimeGameOverlayChannel",
                "HUD",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "PrimeGameOverlayChannel")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        
        val notification = builder
            .setContentTitle("PRIME GAME Active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
            
        startForeground(2001, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>("RESULT_DATA")
        
        if (resultCode == Activity.RESULT_OK && resultData != null && mediaProjection == null) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelf()
                }
            }, mainHandler)
            
            startScreenCapture()
        }
        return START_STICKY
    }

    private fun setupPredictionCanvas() {
        predictionCanvas = PredictionOverlayView(this).apply {
            isAimPredictionEnabled = sharedPreferences.getBoolean("aim_pred", false)
            isCollisionLinesEnabled = sharedPreferences.getBoolean("coin_col", false)
        }
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(predictionCanvas, params)
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

        val rootContainer = FrameLayout(this)

        // Full Menu View
        val fullMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60D131A"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#00E5FF"))
            }
        }

        // Minimized View (Floating Icon)
        val miniMenu = TextView(this).apply {
            text = " PRIME "
            setTextColor(Color.BLACK)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(40, 20, 40, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00E5FF"))
                cornerRadius = 50f
            }
            visibility = View.GONE
        }

        // Header Layout
        val header = RelativeLayout(this)
        
        val titleText = TextView(context).apply {
            text = "PRIME GAME • DRAG TO MOVE"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val btnLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            
            val minBtn = TextView(context).apply {
                text = "– MIN"
                setTextColor(Color.YELLOW)
                textSize = 12f
                setPadding(0, 0, 30, 0)
                setOnClickListener {
                    fullMenu.visibility = View.GONE
                    miniMenu.visibility = View.VISIBLE
                    hudParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                    windowManager.updateViewLayout(rootContainer, hudParams)
                }
            }
            
            val closeBtn = TextView(context).apply {
                text = "× CLOSE"
                setTextColor(Color.LTGRAY)
                textSize = 12f
                setOnClickListener { stopSelf() }
            }
            
            addView(minBtn)
            addView(closeBtn)
        }
        
        val btnParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_END) }
        
        header.addView(titleText)
        header.addView(btnLayout, btnParams)
        
        fullMenu.addView(header)

        // Add Switches helper
        val addFeatureSwitch = { label: String, key: String ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
            }
            val txt = TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val switch = Switch(this).apply {
                isChecked = sharedPreferences.getBoolean(key, false)
                setOnCheckedChangeListener { _, isChecked ->
                    sharedPreferences.edit().putBoolean(key, isChecked).apply()
                    predictionCanvas?.isAimPredictionEnabled = sharedPreferences.getBoolean("aim_pred", false)
                    predictionCanvas?.isCollisionLinesEnabled = sharedPreferences.getBoolean("coin_col", false)
                }
            }
            row.addView(txt)
            row.addView(switch)
            fullMenu.addView(row)
        }

        addFeatureSwitch("Aim prediction", "aim_pred")
        addFeatureSwitch("Coin collision lines", "coin_col")

        // Maximize action
        miniMenu.setOnClickListener {
            miniMenu.visibility = View.GONE
            fullMenu.visibility = View.VISIBLE
            hudParams.width = (screenWidth * 0.82f).toInt()
            windowManager.updateViewLayout(rootContainer, hudParams)
        }

        rootContainer.addView(fullMenu)
        rootContainer.addView(miniMenu)

        // Touch Listener for Dragging
        rootContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
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
                        windowManager.updateViewLayout(rootContainer, hudParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = abs(event.rawX - touchX)
                        val diffY = abs(event.rawY - touchY)
                        if (diffX < 10 && diffY < 10 && miniMenu.visibility == View.VISIBLE) {
                            miniMenu.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        hudRootView = rootContainer
        windowManager.addView(hudRootView, hudParams)
    }

    @SuppressLint("WrongConstant")
    private fun startScreenCapture() {
        val captureWidth = screenWidth / 2
        val captureHeight = screenHeight / 2
        
        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "Stream",
            captureWidth,
            captureHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (isProcessingFrame || predictionCanvas?.isAimPredictionEnabled != true) {
                image.close()
                return@setOnImageAvailableListener
            }
            isProcessingFrame = true
            
            Thread {
                var bitmap: Bitmap? = null
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * captureWidth

                    bitmap = Bitmap.createBitmap(
                        captureWidth + rowPadding / pixelStride,
                        captureHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()
                    
                    val result = carromEngine.processFrame(bitmap, screenWidth, screenHeight)
                    
                    mainHandler.post {
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
                    }
                } catch (e: Throwable) {
                    try { image.close() } catch (ex: Throwable) {}
                } finally {
                    bitmap?.recycle()
                    isProcessingFrame = false
                }
            }.start()
        }, mainHandler)
    }

    override fun onDestroy() {
        super.onDestroy()
        hudRootView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        predictionCanvas?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}
