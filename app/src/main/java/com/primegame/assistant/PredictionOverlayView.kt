package com.primegame.assistant

import android.content.Context
import android.graphics.*
import android.view.View

class PredictionOverlayView(context: Context) : View(context) {
    var isAimPredictionEnabled = false
    var isCollisionLinesEnabled = false

    private var strikerPath: List<PointF>? = null
    private var allCoins: List<CarromEngine.Coin>? = null
    private var targetCoinPath: List<PointF>? = null

    private val linePaint = Paint().apply { color = Color.YELLOW; strokeWidth = 6f; style = Paint.Style.STROKE; isAntiAlias = true }
    private val circlePaint = Paint().apply { color = Color.RED; strokeWidth = 5f; style = Paint.Style.STROKE; isAntiAlias = true }
    private val coinPaintMine = Paint().apply { color = Color.GREEN; strokeWidth = 4f; style = Paint.Style.STROKE; isAntiAlias = true }
    private val coinPaintOpponent = Paint().apply { color = Color.RED; strokeWidth = 4f; style = Paint.Style.STROKE; isAntiAlias = true }

    fun updatePredictions(sPath: List<PointF>?, coins: List<CarromEngine.Coin>?, tPath: List<PointF>?, tPocket: PointF?) {
        strikerPath = sPath
        allCoins = coins
        targetCoinPath = tPath
        invalidate()
    }

    fun clearPredictions() {
        strikerPath = null; allCoins = null; targetCoinPath = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isCollisionLinesEnabled) {
            allCoins?.forEach { coin ->
                val paint = if (coin.isMine) coinPaintMine else coinPaintOpponent
                canvas.drawCircle(coin.pos.x, coin.pos.y, 30f, paint)
            }
        }

        if (isAimPredictionEnabled) {
            strikerPath?.let { path ->
                if (path.size >= 2) {
                    canvas.drawLine(path[0].x, path[0].y, path[1].x, path[1].y, linePaint)
                    // স্ট্রাইকার হিট পয়েন্টে সার্কেল
                    canvas.drawCircle(path[1].x, path[1].y, 35f, circlePaint)
                }
            }
            targetCoinPath?.let { path ->
                if (path.size >= 2) {
                    linePaint.color = Color.CYAN 
                    canvas.drawLine(path[0].x, path[0].y, path[1].x, path[1].y, linePaint)
                    // পকেটের দিকে যাওয়ার লাইনের শেষে সার্কেল
                    canvas.drawCircle(path[1].x, path[1].y, 35f, circlePaint)
                    linePaint.color = Color.YELLOW
                }
            }
        }
    }
}
