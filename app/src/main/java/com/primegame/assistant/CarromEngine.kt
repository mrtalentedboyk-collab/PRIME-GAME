package com.primegame.assistant

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.*

class CarromEngine {

    data class AnalysisResult(
        val strikerTrajectory: List<PointF>,
        val coinTrajectory: List<PointF>,
        val collisionPoint: PointF?,
        val targetPocket: PointF?
    )

    private var lastAngle = 0f
    private var lastStrikerCenter = PointF()

    fun processFrame(bitmap: Bitmap, screenWidth: Int, screenHeight: Int): AnalysisResult? {
        val scaleX = screenWidth.toFloat() / bitmap.width.toFloat()
        val scaleY = screenHeight.toFloat() / bitmap.height.toFloat()
        val boardRect = RectF(45f, (screenHeight - screenWidth) / 2f + 45f, screenWidth - 45f, (screenHeight + screenWidth) / 2f - 45f)

        val pockets = listOf(
            PointF(boardRect.left + 35f, boardRect.top + 35f),
            PointF(boardRect.right - 35f, boardRect.top + 35f),
            PointF(boardRect.left + 35f, boardRect.bottom - 35f),
            PointF(boardRect.right - 35f, boardRect.bottom - 35f)
        )

        // 1. Detect Aim
        val aim = detectAimVector(bitmap, scaleX, scaleY, boardRect) ?: return null
        var (strikerCenter, angleRad) = aim

        // ** ANTI-GLITCH SMOOTHING **
        if (lastAngle != 0f && abs(angleRad - lastAngle) < 0.2f) {
            angleRad = lastAngle * 0.75f + angleRad * 0.25f // Smooth jitter
        }
        lastAngle = angleRad

        // 2. Physics Raycast (Check for coins in path)
        val strikerPath = mutableListOf<PointF>()
        strikerPath.add(strikerCenter)

        var hitCoinPt: PointF? = null
        var stepX = cos(angleRad) * 6f
        var stepY = sin(angleRad) * 6f
        
        var testX = strikerCenter.x / scaleX
        var testY = strikerCenter.y / scaleY
        var hitPixels = 0

        // Scan pixels along the path to find Black or White coins
        for (i in 0..150) {
            testX += (stepX / scaleX)
            testY += (stepY / scaleY)
            if (testX < 10 || testY < 10 || testX > bitmap.width - 10 || testY > bitmap.height - 10) break
            
            val p = bitmap.getPixel(testX.toInt(), testY.toInt())
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            
            // Check for Black/White coin color (different from wooden board)
            if ((r < 70 && g < 70 && b < 70) || (r > 200 && g > 200 && b > 200)) {
                hitPixels++
                if (hitPixels >= 2) { // Confirm it's a coin, not noise
                    hitCoinPt = PointF(testX * scaleX, testY * scaleY)
                    break
                }
            }
        }

        val coinPath = mutableListOf<PointF>()
        var bestPocket: PointF? = null

        if (hitCoinPt != null) {
            // A coin was hit! Stop striker line here.
            strikerPath.add(hitCoinPt)
            
            // Coin path goes towards the pocket that aligns with impact momentum
            bestPocket = pockets.minByOrNull { hypot(it.x - hitCoinPt.x, it.y - hitCoinPt.y) }
            bestPocket?.let {
                coinPath.add(hitCoinPt)
                coinPath.add(it)
            }
        } else {
            // No coin hit, just bounce off the wall
            val wallHit = calculateWallHit(strikerCenter, cos(angleRad), sin(angleRad), boardRect)
            strikerPath.add(wallHit)
            
            var bounceAngle = if (wallHit.x <= boardRect.left + 5f || wallHit.x >= boardRect.right - 5f) PI.toFloat() - angleRad else -angleRad
            val bounceHit = calculateWallHit(wallHit, cos(bounceAngle), sin(bounceAngle), boardRect)
            strikerPath.add(bounceHit)
        }

        return AnalysisResult(strikerPath, coinPath, hitCoinPt, bestPocket)
    }

    private fun detectAimVector(bitmap: Bitmap, scaleX: Float, scaleY: Float, board: RectF): Pair<PointF, Float>? {
        val startY = (bitmap.height * 0.58f).toInt(); val endY = (bitmap.height * 0.74f).toInt()
        var ax = 0f; var ay = 0f; var count = 0

        for (y in startY until endY step 3) {
            for (x in 20 until bitmap.width - 20 step 3) {
                val p = bitmap.getPixel(x, y)
                if (Color.red(p) > 215 && Color.green(p) in 20..90 && Color.blue(p) < 50) {
                    ax += x; ay += y; count++
                }
            }
        }
        if (count < 14) return null

        val headPt = PointF((ax / count) * scaleX, (ay / count) * scaleY)
        val strikerOrigin = PointF(headPt.x, board.bottom - (board.height() * 0.22f))
        return Pair(strikerOrigin, atan2(headPt.y - strikerOrigin.y, headPt.x - strikerOrigin.x))
    }

    private fun calculateWallHit(origin: PointF, dirX: Float, dirY: Float, board: RectF): PointF {
        var minT = Float.MAX_VALUE
        if (dirX > 0) minT = minOf(minT, (board.right - origin.x) / dirX) else if (dirX < 0) minT = minOf(minT, (board.left - origin.x) / dirX)
        if (dirY > 0) minT = minOf(minT, (board.bottom - origin.y) / dirY) else if (dirY < 0) minT = minOf(minT, (board.top - origin.y) / dirY)
        return PointF(origin.x + dirX * minT, origin.y + dirY * minT)
    }
}
