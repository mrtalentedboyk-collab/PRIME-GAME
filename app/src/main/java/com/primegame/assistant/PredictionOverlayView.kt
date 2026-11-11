package com.primegame.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.view.View

class PredictionOverlayView(context: Context) : View(context) {

    // Default: ALL OFF
    var isAimPredictionEnabled = false
    var isCollisionLinesEnabled = false
    var isBankShotEnabled = false
    var isPowerGuideEnabled = false

    var strikerPath = mutableListOf<PointF>()
    var targetCoinPath = mutableListOf<PointF>()
    var collisionPoint: PointF? = null
    var targetPocket: PointF? = null

    private val primaryLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF") // Cyan BitAim Style
        strokeWidth = 7f
        style = Paint.Style.STROKE
    }

    private val bounceLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE // Multi-line reflection
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val coinLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700") // Gold coin trajectory
        strokeWidth = 6f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
    }

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    fun updatePredictions(
        sLines: List<PointF>,
        cLines: List<PointF>,
        hit: PointF?,
        pocket: PointF?
    ) {
        strikerPath.clear()
        strikerPath.addAll(sLines)
        targetCoinPath.clear()
        targetCoinPath.addAll(cLines)
        collisionPoint = hit
        targetPocket = pocket
        postInvalidate()
    }

    fun clearPredictions() {
        strikerPath.clear()
        targetCoinPath.clear()
        collisionPoint = null
        targetPocket = null
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isAimPredictionEnabled) return

        // 1. Multi-line Striker Path
        if (strikerPath.size >= 2) {
            for (i in 0 until strikerPath.size - 1) {
                val start = strikerPath[i]
                val end = strikerPath[i + 1]
                val paint = if (i == 0) primaryLinePaint else (if (isBankShotEnabled) bounceLinePaint else null)
                paint?.let { canvas.drawLine(start.x, start.y, end.x, end.y, it) }
            }
        }

        // 2. Collision Impact
        collisionPoint?.let { pt ->
            if (isCollisionLinesEnabled) {
                canvas.drawCircle(pt.x, pt.y, 16f, centerDotPaint)
                canvas.drawCircle(pt.x, pt.y, 28f, indicatorPaint)
            }
        }

        // 3. Multi-line Coin Path to Pocket
        if (isCollisionLinesEnabled && targetCoinPath.size >= 2) {
            for (i in 0 until targetCoinPath.size - 1) {
                val start = targetCoinPath[i]
                val end = targetCoinPath[i + 1]
                canvas.drawLine(start.x, start.y, end.x, end.y, coinLinePaint)
            }
        }

        // 4. Targeted Pocket Visual
        targetPocket?.let { pkt ->
            if (isCollisionLinesEnabled) {
                canvas.drawCircle(pkt.x, pkt.y, 40f, indicatorPaint)
            }
        }
    }
}
