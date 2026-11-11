package com.primegame.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.View

class PredictionOverlayView(context: Context) : View(context) {

    var isAimPredictionEnabled = false
    var isCollisionLinesEnabled = false
    var isBankShotEnabled = false
    var isPowerGuideEnabled = false

    var strikerPath = mutableListOf<PointF>()
    var targetCoinPath = mutableListOf<PointF>()
    var collisionPoint: PointF? = null
    var targetPocket: PointF? = null

    // BitAim Yellow Striker Line
    private val strikerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    // BitAim Cyan Coin & Reflection Line
    private val coinLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    // Target Pocket & Collision Ring
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

        // 1. Draw Striker Path (Yellow primary, Cyan bank bounce)
        if (strikerPath.size >= 2) {
            for (i in 0 until strikerPath.size - 1) {
                val start = strikerPath[i]
                val end = strikerPath[i + 1]
                val paint = if (i == 0) strikerLinePaint else (if (isBankShotEnabled) coinLinePaint else null)
                paint?.let { canvas.drawLine(start.x, start.y, end.x, end.y, it) }
            }
        }

        // 2. Draw Impact Point Markers
        collisionPoint?.let { pt ->
            if (isCollisionLinesEnabled) {
                canvas.drawCircle(pt.x, pt.y, 14f, innerDotPaint)
                canvas.drawCircle(pt.x, pt.y, 28f, outerRingPaint)
            }
        }

        // 3. Draw Multi-line Coin Trajectory to Pocket
        if (isCollisionLinesEnabled && targetCoinPath.size >= 2) {
            for (i in 0 until targetCoinPath.size - 1) {
                val start = targetCoinPath[i]
                val end = targetCoinPath[i + 1]
                canvas.drawLine(start.x, start.y, end.x, end.y, coinLinePaint)
            }
        }

        // 4. Target Pocket Marker
        targetPocket?.let { pkt ->
            if (isCollisionLinesEnabled) {
                canvas.drawCircle(pkt.x, pkt.y, 42f, outerRingPaint)
                canvas.drawCircle(pkt.x, pkt.y, 18f, innerDotPaint)
            }
        }
    }
}
