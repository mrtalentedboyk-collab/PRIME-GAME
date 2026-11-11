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
        val boardRect = RectF(0f, boardTop, screenWidth.toFloat(), boardBottom)

        val pockets = listOf(
            PointF(boardRect.left + 50f, boardRect.top + 50f),
            PointF(boardRect.right - 50f, boardRect.top + 50f),
            PointF(boardRect.left + 50f, boardRect.bottom - 50f),
            PointF(boardRect.right - 50f, boardRect.bottom - 50f)
        )

        val aimData = detectStrikerAim(bitmap, boardRect) ?: return null
        val (origin, angleRad) = aimData

        val strikerPath = mutableListOf<PointF>()
        strikerPath.add(origin)

        var currentPos = PointF(origin.x, origin.y)
        var currentAngle = angleRad
        var remainingDist = 1200f

        for (bounce in 0..2) {
            val stepX = cos(currentAngle)
            val stepY = sin(currentAngle)
            val nextHit = calculateWallIntersection(currentPos, stepX, stepY, boardRect)

            val segDist = hypot(nextHit.x - currentPos.x, nextHit.y - currentPos.y)
            if (segDist >= remainingDist) {
                strikerPath.add(PointF(currentPos.x + stepX * remainingDist, currentPos.y + stepY * remainingDist))
                break
            } else {
                strikerPath.add(nextHit)
                remainingDist -= segDist
                currentPos = nextHit
                
                if (nextHit.x <= boardRect.left + 35f || nextHit.x >= boardRect.right - 35f) {
                    currentAngle = PI.toFloat() - currentAngle
                } else {
                    currentAngle = -currentAngle
                }
            }
        }

        val targetPocket = pockets.minByOrNull { hypot(it.x - strikerPath.last().x, it.y - strikerPath.last().y) }

        return AnalysisResult(
            strikerTrajectory = strikerPath,
            coinTrajectory = listOf(),
            collisionPoint = if (strikerPath.size > 1) strikerPath[1] else null,
            targetPocket = targetPocket
        )
    }

    private fun detectStrikerAim(bitmap: Bitmap, board: RectF): Pair<PointF, Float>? {
        val scanYStart = (board.bottom - (board.height() * 0.35f)).toInt().coerceIn(0, bitmap.height - 1)
        val scanYEnd = (board.bottom - (board.height() * 0.10f)).toInt().coerceIn(0, bitmap.height - 1)
        
        var arrowX = 0f
        var arrowY = 0f
        var count = 0

        val step = 4
        for (y in scanYStart until scanYEnd step step) {
            for (x in 20 until bitmap.width - 20 step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                if (r > 200 && g in 40..140 && b < 50) {
                    arrowX += x
                    arrowY += y
                    count++
                }
            }
        }

        if (count < 15) return null

        val detectedCenter = PointF(arrowX / count, arrowY / count)
        val targetCenter = PointF(board.centerX(), board.centerY())
        val angle = atan2(targetCenter.y - detectedCenter.y, targetCenter.x - detectedCenter.x)

        return Pair(detectedCenter, angle)
    }

    private fun calculateWallIntersection(origin: PointF, dirX: Float, dirY: Float, board: RectF): PointF {
        val margin = 40f
        var minT = Float.MAX_VALUE

        if (dirX > 0) {
            val t = (board.right - margin - origin.x) / dirX
            if (t > 0 && t < minT) minT = t
        } else if (dirX < 0) {
            val t = (board.left + margin - origin.x) / dirX
            if (t > 0 && t < minT) minT = t
        }

        if (dirY > 0) {
            val t = (board.bottom - margin - origin.y) / dirY
            if (t > 0 && t < minT) minT = t
        } else if (dirY < 0) {
            val t = (board.top + margin - origin.y) / dirY
            if (t > 0 && t < minT) minT = t
        }

        return PointF(origin.x + dirX * minT, origin.y + dirY * minT)
    }
}
