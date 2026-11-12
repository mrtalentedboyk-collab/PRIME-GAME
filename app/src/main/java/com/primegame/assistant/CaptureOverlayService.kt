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
import android.provider.Settings
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
    
    private var autoPlayLockTime = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences("PrimeGamePrefs", Context.MODE_PRIVATE)
        val metrics = DisplayMetrics()
        (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(0)?.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        startForegroundServiceNotification()
        setupPredictionCanvas()
        setupFloatingHud()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "PrimeGameOverlayChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "PRIME GAME HUD", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Running overlay assistant"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setContentTitle("PRIME GAME Active")
         .setContentText("Overlay and Bot running")
         .setSmallIcon(android.R.drawable.ic_menu_compass)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2001, builder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(2001, builder.build())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>("RESULT_DATA")
        
        if (resultCode == Activity.RESULT_OK && resultData != null && mediaProjection == null) {
            try {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        stopSelf()
                    }
                }, mainHandler)
                startScreenCapture()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return START_STICKY
    }

    private fun setupPredictionCanvas() {
        predictionCanvas = PredictionOverlayView(this).apply {
            isAimPredictionEnabled = sharedPreferences.getBoolean("aim_pred", false)
            isCollisionLinesEnabled = sharedPreferences.getBoolean("coin_col", false)
        }
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        windowManager.addView(predictionCanvas, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingHud() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val hudParams = WindowManager.LayoutParams((screenWidth * 0.82f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 120 }

        val rootContainer = FrameLayout(this)
        val fullMenu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 24); background = GradientDrawable().apply { setColor(Color.parseColor("#E60D131A")); cornerRadius = 24f; setStroke(2, Color.parseColor("#00E5FF")) } }
        val miniMenu = TextView(this).apply { text = " PRIME "; setTextColor(Color.BLACK); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setPadding(40, 20, 40, 20); background = GradientDrawable().apply { setColor(Color.parseColor("#00E5FF")); cornerRadius = 50f }; visibility = View.GONE }
        
        val header = RelativeLayout(this)
        val btnLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply { text = "– MIN"; setTextColor(Color.YELLOW); textSize = 12f; setPadding(0, 0, 30, 0); setOnClickListener { fullMenu.visibility = View.GONE; miniMenu.visibility = View.VISIBLE; hudParams.width = WindowManager.LayoutParams.WRAP_CONTENT; windowManager.updateViewLayout(rootContainer, hudParams) } })
            addView(TextView(context).apply { text = "× CLOSE"; setTextColor(Color.LTGRAY); textSize = 12f; setOnClickListener { stopSelf() } })
        }
        header.addView(TextView(this).apply { text = "PRIME GAME • DRAG TO MOVE"; setTextColor(Color.parseColor("#00E5FF")); textSize = 13f; typeface = Typeface.DEFAULT_BOLD })
        header.addView(btnLayout, RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply { addRule(RelativeLayout.ALIGN_PARENT_END) })
        fullMenu.addView(header)

        val addFeatureSwitch = { label: String, key: String ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 10, 0, 10) }
            val txt = TextView(this).apply { text = label; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            val switch = Switch(this).apply {
                isChecked = sharedPreferences.getBoolean(key, false)
                setOnCheckedChangeListener { _, isChecked ->
                    sharedPreferences.edit().putBoolean(key, isChecked).apply()
                    if (key == "aim_pred") predictionCanvas?.isAimPredictionEnabled = isChecked
                    if (key == "coin_col") predictionCanvas?.isCollisionLinesEnabled = isChecked
                    if (key == "auto_play") {
                        if(isChecked && AutoPlayService.instance == null) {
                            Toast.makeText(this@CaptureOverlayService, "Enable Accessibility in Settings", Toast.LENGTH_LONG).show()
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                            this.isChecked = false
                        }
                    }
                }
            }
            row.addView(txt); row.addView(switch); fullMenu.addView(row)
        }

        addFeatureSwitch("Aim prediction", "aim_pred")
        addFeatureSwitch("Coin collision lines", "coin_col")
        addFeatureSwitch("Auto Play (Bot)", "auto_play")

        miniMenu.setOnClickListener { miniMenu.visibility = View.GONE; fullMenu.visibility = View.VISIBLE; hudParams.width = (screenWidth * 0.82f).toInt(); windowManager.updateViewLayout(rootContainer, hudParams) }
        rootContainer.addView(fullMenu); rootContainer.addView(miniMenu)

        rootContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0; private var touchX = 0f; private var touchY = 0f
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { initialX = hudParams.x; initialY = hudParams.y; touchX = event.rawX; touchY = event.rawY; return true }
                    MotionEvent.ACTION_MOVE -> { hudParams.x = initialX + (event.rawX - touchX).toInt(); hudParams.y = initialY + (event.rawY - touchY).toInt(); windowManager.updateViewLayout(rootContainer, hudParams); return true }
                    MotionEvent.ACTION_UP -> { if (abs(event.rawX - touchX) < 10 && abs(event.rawY - touchY) < 10 && miniMenu.visibility == View.VISIBLE) miniMenu.performClick(); return true }
                }
                return false
            }
        })
        hudRootView = rootContainer
        windowManager.addView(hudRootView, hudParams)
    }

    @SuppressLint("WrongConstant")
    private fun startScreenCapture() {
        if (mediaProjection == null) return
        val captureWidth = screenWidth / 2; val captureHeight = screenHeight / 2
        try {
            imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay("Stream", captureWidth, captureHeight, screenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, mainHandler)

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val isAutoPlayOn = sharedPreferences.getBoolean("auto_play", false)
                if (isProcessingFrame || (!predictionCanvas!!.isAimPredictionEnabled && !isAutoPlayOn && !predictionCanvas!!.isCollisionLinesEnabled)) { 
                    image.close(); return@setOnImageAvailableListener 
                }
                isProcessingFrame = true
                
                Thread {
                    var bitmap: Bitmap? = null
                    try {
                        val buffer = image.planes[0].buffer
                        val pixelStride = image.planes[0].pixelStride
                        val rowStride = image.planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * captureWidth

                        bitmap = Bitmap.createBitmap(captureWidth + rowPadding / pixelStride, captureHeight, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()
                        
                        val result = carromEngine.processFrame(bitmap, captureWidth, captureHeight, isAutoPlayOn)
                        
                        mainHandler.post {
                            if (result != null) {
                                val scaleX = 2f; val scaleY = 2f
                                val scaledStriker = result.strikerTrajectory.map { PointF(it.x * scaleX, it.y * scaleY) }
                                val scaledTarget = result.targetCoinPath?.map { PointF(it.x * scaleX, it.y * scaleY) }
                                val scaledCoins = result.allCoins.map { CarromEngine.Coin(PointF(it.pos.x * scaleX, it.pos.y * scaleY), it.isMine) }

                                predictionCanvas?.updatePredictions(scaledStriker, scaledCoins, scaledTarget, null)
                                
                                // অপনেন্ট নয়, শুধুমাত্র নিজের গুটি পকেটে ফেলার জন্য ম্যাচ উইন পর্যন্ত টানা অটো-প্লে লজিক
                                if (isAutoPlayOn && result.isBotAiming && scaledStriker.isNotEmpty()) {
                                    val currentTime = System.currentTimeMillis()
                                    if (autoPlayLockTime == 0L) {
                                        autoPlayLockTime = currentTime
                                    } else if (currentTime - autoPlayLockTime > 900) { 
                                        AutoPlayService.performShot(scaledStriker[0].x, scaledStriker[0].y, result.aimAngle, 460f)
                                        autoPlayLockTime = currentTime + 1500 // পরবর্তী শটের জন্য কুডাউন গ্যাপ
                                    }
                                } else if (!isAutoPlayOn) {
                                    autoPlayLockTime = 0L
                                }
                            } else {
                                predictionCanvas?.clearPredictions()
                                autoPlayLockTime = 0L
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hudRootView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        predictionCanvas?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}
