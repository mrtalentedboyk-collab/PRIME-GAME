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
        val scaleX = screenWidth.toFloat() / bitmap.width.toFloat()
        val scaleY = screenHeight.toFloat() / bitmap.height.toFloat()

        val boardSize = screenWidth.toFloat()
        val boardTop = (screenHeight - boardSize) / 2f
        val boardBottom = boardTop + boardSize
        val boardRect = RectF(40f, boardTop + 40f, screenWidth - 40f, boardBottom - 40f)

        val pockets = listOf(
            PointF(boardRect.left + 35f, boardRect.top + 35f),
            PointF(boardRect.right - 35f, boardRect.top + 35f),
            PointF(boardRect.left + 35f, boardRect.bottom - 35f),
            PointF(boardRect.right - 35f, boardRect.bottom - 35f)
        )

        // 1. Detect Striker and Aim Vector from scaled frame
        val aim = detectAimVector(bitmap, scaleX, scaleY, boardRect) ?: return null
        val (strikerCenter, angleRad) = aim

        // 2. Generate Multi-line Striker Raycast
        val strikerPath = mutableListOf<PointF>()
        strikerPath.add(strikerCenter)

        var cur = PointF(strikerCenter.x, strikerCenter.y)
        var curAngle = angleRad
        var remainingDist = 2500f
        var hitPoint: PointF? = null
        val coinPath = mutableListOf<PointF>()
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

                // Bounce angle inversion
                if (nextHit.x <= boardRect.left + 5f || nextHit.x >= boardRect.right - 5f) {
                    curAngle = PI.toFloat() - curAngle
                } else {
                    curAngle = -curAngle
                }
            }
        }

        // 3. Collision Point & Pocket Path
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

    private fun detectAimVector(
        bitmap: Bitmap,
        scaleX: Float,
        scaleY: Float,
        board: RectF
    ): Pair<PointF, Float>? {
        val bmpW = bitmap.width
        val bmpH = bitmap.height

        // Area where striker baseline exists
        val startY = (bmpH * 0.50f).toInt().coerceIn(0, bmpH - 1)
        val endY = (bmpH * 0.78f).toInt().coerceIn(0, bmpH - 1)

        var arrowHeadX = 0f
        var arrowHeadY = 0f
        var arrowHeadCount = 0

        var arrowTailX = 0f
        var arrowTailY = 0f
        var arrowTailCount = 0

        val step = 3
        for (y in startY until endY step step) {
            for (x in 10 until bmpW - 10 step step) {
                val p = bitmap.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)

                // Detect Red Arrow Head
                if (r > 190 && g < 75 && b < 75) {
                    arrowHeadX += x
                    arrowHeadY += y
                    arrowHeadCount++
                }
                // Detect Yellow/Orange Body of Arrow
                else if (r > 200 && g in 100..200 && b < 60) {
                    arrowTailX += x
                    arrowTailY += y
                    arrowTailCount++
                }
            }
        }

        // Validate detected guide arrow
        if (arrowHeadCount < 6 && arrowTailCount < 6) return null

        val headPt = if (arrowHeadCount >= 6) {
            PointF((arrowHeadX / arrowHeadCount) * scaleX, (arrowHeadY / arrowHeadCount) * scaleY)
        } else {
            PointF((arrowTailX / arrowTailCount) * scaleX, (arrowTailY / arrowTailCount) * scaleY)
        }

        val baseStrikerY = board.bottom - (board.height() * 0.22f)
        val strikerOrigin = PointF(headPt.x, baseStrikerY)

        val angle = atan2(headPt.y - strikerOrigin.y, headPt.x - strikerOrigin.x)

        return Pair(strikerOrigin, angle)
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
