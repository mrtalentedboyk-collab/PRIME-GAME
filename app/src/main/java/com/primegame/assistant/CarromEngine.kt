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
        val targetPocket: PointF?,
        val aimAngle: Float
    )

    private var lastAngle = 0f

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

        // 1. Detect Exact Aim Vector using Yellow-Tail to Red-Head gradient logic
        val aim = detectAimVector(bitmap, scaleX, scaleY, boardRect) ?: return null
        var (strikerCenter, angleRad) = aim

        // Anti-Glitch Smoothing
        if (lastAngle != 0f && abs(angleRad - lastAngle) < 0.3f) {
            angleRad = lastAngle * 0.8f + angleRad * 0.2f
        }
        lastAngle = angleRad

        // 2. Physics Raycast
        val strikerPath = mutableListOf<PointF>()
        strikerPath.add(strikerCenter)

        var hitCoinPt: PointF? = null
        var stepX = cos(angleRad) * 5f
        var stepY = sin(angleRad) * 5f
        
        var testX = strikerCenter.x / scaleX
        var testY = strikerCenter.y / scaleY
        var hitPixels = 0

        for (i in 0..180) {
            testX += (stepX / scaleX)
            testY += (stepY / scaleY)
            if (testX < 10 || testY < 10 || testX > bitmap.width - 10 || testY > bitmap.height - 10) break
            
            val p = bitmap.getPixel(testX.toInt(), testY.toInt())
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            
            // Detect coins (Black/White) on the path
            if ((r < 75 && g < 75 && b < 75) || (r > 200 && g > 200 && b > 200)) {
                hitPixels++
                if (hitPixels >= 2) {
                    hitCoinPt = PointF(testX * scaleX, testY * scaleY)
                    break
                }
            }
        }

        val coinPath = mutableListOf<PointF>()
        var bestPocket: PointF? = null

        if (hitCoinPt != null) {
            strikerPath.add(hitCoinPt)
            bestPocket = pockets.minByOrNull { hypot(it.x - hitCoinPt.x, it.y - hitCoinPt.y) }
            bestPocket?.let {
                coinPath.add(hitCoinPt)
                coinPath.add(it)
            }
        } else {
            val wallHit = calculateWallHit(strikerCenter, cos(angleRad), sin(angleRad), boardRect)
            strikerPath.add(wallHit)
            var bounceAngle = if (wallHit.x <= boardRect.left + 5f || wallHit.x >= boardRect.right - 5f) PI.toFloat() - angleRad else -angleRad
            val bounceHit = calculateWallHit(wallHit, cos(bounceAngle), sin(bounceAngle), boardRect)
            strikerPath.add(bounceHit)
        }

        return AnalysisResult(strikerPath, coinPath, hitCoinPt, bestPocket, angleRad)
    }

    private fun detectAimVector(bitmap: Bitmap, scaleX: Float, scaleY: Float, board: RectF): Pair<PointF, Float>? {
        val startY = (board.top / scaleY).toInt().coerceIn(0, bitmap.height - 1)
        val endY = (board.bottom / scaleY).toInt().coerceIn(0, bitmap.height - 1)
        
        var headX = 0f; var headY = 0f; var headCount = 0
        var tailX = 0f; var tailY = 0f; var tailCount = 0

        for (y in startY until endY step 3) {
            for (x in 20 until bitmap.width - 20 step 3) {
                val p = bitmap.getPixel(x, y)
                val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                
                // Head (Red/Dark Orange)
                if (r > 200 && g < 100 && b < 80) {
                    headX += x; headY += y; headCount++
                }
                // Tail (Yellow/Bright Orange) near striker
                else if (r > 200 && g > 150 && b < 80) {
                    tailX += x; tailY += y; tailCount++
                }
            }
        }

        if (headCount < 4 || tailCount < 4) return null

        val hX = (headX / headCount) * scaleX
        val hY = (headY / headCount) * scaleY
        val tX = (tailX / tailCount) * scaleX
        val tY = (tailY / tailCount) * scaleY

        // Angle perfectly follows the arrow from tail to head
        val angle = atan2(hY - tY, hX - tX)
        val strikerCenter = PointF(tX, tY) 

        return Pair(strikerCenter, angle)
    }

    private fun calculateWallHit(origin: PointF, dirX: Float, dirY: Float, board: RectF): PointF {
        var minT = Float.MAX_VALUE
        if (dirX > 0) minT = minOf(minT, (board.right - origin.x) / dirX) else if (dirX < 0) minT = minOf(minT, (board.left - origin.x) / dirX)
        if (dirY > 0) minT = minOf(minT, (board.bottom - origin.y) / dirY) else if (dirY < 0) minT = minOf(minT, (board.top - origin.y) / dirY)
        return PointF(origin.x + dirX * minT, origin.y + dirY * minT)
    }
}
