package com.primegame.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.View

class PredictionOverlayView(context: Context) : View(context) {

    var isAimPredictionEnabled = true
    var isCollisionLinesEnabled = true
    var isBankShotEnabled = true
    var isPowerGuideEnabled = true

    var boardRect = RectF()
    var strikerPath = mutableListOf<PointF>()
    var targetCoinPath = mutableListOf<PointF>()
    var collisionPoint: PointF? = null
    var targetPocket: PointF? = null

    private val strikerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val coinPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    private val bankPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3D00")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    fun updatePredictions(
        sLines: List<PointF>,
        cLines: List<PointF>,
        hitPoint: PointF?,
        pocket: PointF?
    ) {
        strikerPath.clear()
        strikerPath.addAll(sLines)
        targetCoinPath.clear()
        targetCoinPath.addAll(cLines)
        collisionPoint = hitPoint
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

        if (strikerPath.size >= 2) {
            for (i in 0 until strikerPath.size - 1) {
                val start = strikerPath[i]
                val end = strikerPath[i + 1]
                val paint = if (i > 0 && isBankShotEnabled) bankPathPaint else strikerPaint
                canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            }
        }

        collisionPoint?.let { pt ->
            if (isCollisionLinesEnabled) {
                canvas.drawCircle(pt.x, pt.y, 14f, pointPaint)
                canvas.drawCircle(pt.x, pt.y, 22f, ringPaint)
            }
        }

        if (isCollisionLinesEnabled && targetCoinPath.size >= 2) {
            for (i in 0 until targetCoinPath.size - 1) {
                val start = targetCoinPath[i]
                val end = targetCoinPath[i + 1]
                canvas.drawLine(start.x, start.y, end.x, end.y, coinPathPaint)
            }
        }

        targetPocket?.let { pkt ->
            canvas.drawCircle(pkt.x, pkt.y, 35f, ringPaint)
        }
    }
}
