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

    fun processFrame(bitmap: Bitmap, screenWidth: Int, screenHeight: Int): AnalysisResult? {
        val boardSize = screenWidth.toFloat()
        val boardTop = (screenHeight - boardSize) / 2f
        val boardBottom = boardTop + boardSize
        val boardRect = RectF(40f, boardTop + 40f, screenWidth - 40f, boardBottom - 40f)

        val pockets = listOf(
            PointF(boardRect.left + 30f, boardRect.top + 30f),
            PointF(boardRect.right - 30f, boardRect.top + 30f),
            PointF(boardRect.left + 30f, boardRect.bottom - 30f),
            PointF(boardRect.right - 30f, boardRect.bottom - 30f)
        )

        // 1. Detect Striker Guide Arrow
        val aim = detectAimVector(bitmap, boardRect) ?: return null
        val (strikerCenter, angleRad) = aim

        // 2. Multi-line Striker Raycast (Bounces)
        val strikerPath = mutableListOf<PointF>()
        strikerPath.add(strikerCenter)

        var cur = PointF(strikerCenter.x, strikerCenter.y)
        var curAngle = angleRad
        var remainingDist = 2200f
        var hitPoint: PointF? = null
        var coinPath = mutableListOf<PointF>()
        var bestPocket: PointF? = null

        for (bounce in 0..3) {
            val dirX = cos(curAngle)
            val dirY = sin(curAngle)
            val nextHit = calculateWallHit(cur, dirX, dirY, boardRect)

            val segDist = hypot(nextHit.x - cur.x, nextHit.y - cur.y)
            if (segDist >= remainingDist) {
                strikerPath.add(PointF(cur.x + dirX * remainingDist, cur.y + dirY * remainingDist))
                break
            } else {
                strikerPath.add(nextHit)
                remainingDist -= segDist
                cur = nextHit

                // Wall Reflection
                if (nextHit.x <= boardRect.left + 5f || nextHit.x >= boardRect.right - 5f) {
                    curAngle = PI.toFloat() - curAngle
                } else {
                    curAngle = -curAngle
                }
            }
        }

        // 3. Multi-line Coin deflection towards pocket
        if (strikerPath.size > 1) {
            hitPoint = strikerPath[1]
            bestPocket = pockets.minByOrNull { hypot(it.x - hitPoint.x, it.y - hitPoint.y) }
            bestPocket?.let {
                coinPath.add(hitPoint)
                coinPath.add(it)
            }
        }

        return AnalysisResult(
            strikerTrajectory = strikerPath,
            coinTrajectory = coinPath,
            collisionPoint = hitPoint,
            targetPocket = bestPocket
        )
    }

    private fun detectAimVector(bitmap: Bitmap, board: RectF): Pair<PointF, Float>? {
        val yStart = (board.bottom - board.height() * 0.40f).toInt().coerceIn(0, bitmap.height - 1)
        val yEnd = (board.bottom - board.height() * 0.05f).toInt().coerceIn(0, bitmap.height - 1)

        var sumX = 0f
        var sumY = 0f
        var count = 0

        val step = 3
        for (y in yStart until yEnd step step) {
            for (x in 20 until bitmap.width - 20 step step) {
                val p = bitmap.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)

                // Precise arrow color filter (Orange / Red Guide line)
                if (r > 190 && g in 40..150 && b < 60) {
                    sumX += x
                    sumY += y
                    count++
                }
            }
        }

        if (count < 12) return null

        val arrowCenter = PointF(sumX / count, sumY / count)
        val boardCenter = PointF(board.centerX(), board.centerY())
        val angle = atan2(boardCenter.y - arrowCenter.y, boardCenter.x - arrowCenter.x)

        return Pair(arrowCenter, angle)
    }

    private fun calculateWallHit(origin: PointF, dirX: Float, dirY: Float, board: RectF): PointF {
        var minT = Float.MAX_VALUE

        if (dirX > 0) {
            val t = (board.right - origin.x) / dirX
            if (t > 0 && t < minT) minT = t
        } else if (dirX < 0) {
            val t = (board.left - origin.x) / dirX
            if (t > 0 && t < minT) minT = t
        }

        if (dirY > 0) {
            val t = (board.bottom - origin.y) / dirY
            if (t > 0 && t < minT) minT = t
        } else if (dirY < 0) {
            val t = (board.top - origin.y) / dirY
            if (t > 0 && t < minT) minT = t
        }

        return PointF(origin.x + dirX * minT, origin.y + dirY * minT)
    }
}
