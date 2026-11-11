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
        (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(0).getRealMetrics(metrics)
        screenWidth = metrics.widthPixels; screenHeight = metrics.heightPixels; screenDensity = metrics.densityDpi
        
        startForeground(2001, Notification.Builder(this, "PrimeGameOverlayChannel").apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("PrimeGameOverlayChannel", "HUD", NotificationManager.IMPORTANCE_LOW))
            }
            setContentTitle("PRIME GAME Active")
            setSmallIcon(android.R.drawable.ic_menu_compass)
        }.build())
        
        setupPredictionCanvas()
        setupFloatingHud()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>("RESULT_DATA")
        if (resultCode == Activity.RESULT_OK && resultData != null && mediaProjection == null) {
            mediaProjection = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(resultCode, resultData)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, mainHandler)
            startScreenCapture()
        }
        return START_STICKY
    }

    private fun setupPredictionCanvas() {
        predictionCanvas = PredictionOverlayView(this).apply {
            isAimPredictionEnabled = sharedPreferences.getBoolean("aim_pred", false)
            isCollisionLinesEnabled = sharedPreferences.getBoolean("coin_col", false)
        }
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        windowManager.addView(predictionCanvas, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingHud() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val hudParams = WindowManager.LayoutParams((screenWidth * 0.82f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT, layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 120 }

        val rootContainer = FrameLayout(this)

        val fullMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            background = GradientDrawable().apply { setColor(Color.parseColor("#E60D131A")); cornerRadius = 24f; setStroke(2, Color.parseColor("#00E5FF")) }
        }

        val miniMenu = TextView(this).apply {
            text = " PRIME "
            setTextColor(Color.BLACK)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(40, 20, 40, 20)
            background = GradientDrawable().apply { setColor(Color.parseColor("#00E5FF")); cornerRadius = 50f }
            visibility = View.GONE
        }

        val header = RelativeLayout(this).apply {
            addView(TextView(context).apply { text = "PRIME GAME • DRAG TO MOVE"; setTextColor(Color.parseColor("#00E5FF")); textSize = 13f; typeface = Typeface.DEFAULT_BOLD })
            val btnLayout = LinearLayout(context).apply { 
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(context).apply { text = "– MIN"; setTextColor(Color.YELLOW); textSize = 12f; setPadding(0,0,30,0); setOnClickListener { fullMenu.visibility = View.GONE; miniMenu.visibility = View.VISIBLE; hudParams.width = WindowManager.LayoutParams.WRAP_CONTENT; windowManager.updateViewLayout(rootContainer, hudParams) }})
                addView(TextView(context).apply { text = "× CLOSE"; setTextColor(Color.LTGRAY); textSize = 12f; setOnClickListener { stopSelf() }})
            }
            addView(btnLayout, RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply { addRule(RelativeLayout.ALIGN_PARENT_END) })
        }
        fullMenu.addView(header)

        fun addSwitch(label: String, key: String) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 10, 0, 10) }
            row.addView(TextView(this).apply { text = label; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(Switch(this).apply {
                isChecked = sharedPreferences.getBoolean(key, false)
                setOnCheckedChangeListener { _, c -> sharedPreferences.edit().putBoolean(key, c).apply()
                    predictionCanvas?.isAimPredictionEnabled = sharedPreferences.getBoolean("aim_pred", false)
                    predictionCanvas?.isCollisionLinesEnabled = sharedPreferences.getBoolean("coin_col", false)
                }
            })
            fullMenu.addView(row)
        }

        addSwitch("Aim prediction", "aim_pred")
        addSwitch("Coin collision lines", "coin_col")

        miniMenu.setOnClickListener {
            miniMenu.visibility = View.GONE
            fullMenu.visibility = View.VISIBLE
            hudParams.width = (screenWidth * 0.82f).toInt()
            windowManager.updateViewLayout(rootContainer, hudParams)
        }

        rootContainer.addView(fullMenu)
        rootContainer.addView(miniMenu)

        rootContainer.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0; var initialY = 0; var touchX = 0f;
