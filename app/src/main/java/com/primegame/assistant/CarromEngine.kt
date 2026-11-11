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
        val boardRect = RectF(45f, boardTop + 45f, screenWidth - 45f, boardBottom - 45f)

        val pockets = listOf(
            PointF(boardRect.left + 35f, boardRect.top + 35f),
            PointF(boardRect.right - 35f, boardRect.top + 35f),
            PointF(boardRect.left + 35f, boardRect.bottom - 35f),
            PointF(boardRect.right - 35f, boardRect.bottom - 35f)
        )

        // 1. Detect Striker and Aim Vector (Strictly in the baseline control area)
        val aim = detectAimVector(bitmap, scaleX, scaleY, boardRect) ?: return null
        val (strikerCenter, angleRad) = aim

        // 2. Striker Raycast Trajectory
        val strikerPath = mutableListOf<PointF>()
        strikerPath.add(strikerCenter)

        var cur = PointF(strikerCenter.x, strikerCenter.y)
        var curAngle = angleRad
        var remainingDist = 2000f
        var hitPoint: PointF? = null
        val coinPath = mutableListOf<PointF>()
        var bestPocket: PointF? = null

        for (bounce in 0..2) {
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

                // Wall reflection
                if (nextHit.x <= boardRect.left + 5f || nextHit.x >= boardRect.right - 5f) {
                    curAngle = PI.toFloat() - curAngle
                } else {
                    curAngle = -curAngle
                }
            }
        }

        // 3. Multi-line Collision Point & Target Pocket Raycast
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

        // Scan exclusively around the player's lower striker baseline (exclude board center)
        val startY = (bmpH * 0.58f).toInt().coerceIn(0, bmpH - 1)
        val endY = (bmpH * 0.74f).toInt().coerceIn(0, bmpH - 1)

        var arrowHeadX = 0f
        var arrowHeadY = 0f
        var arrowCount = 0

        val step = 3
        for (y in startY until endY step step) {
            for (x in 20 until bmpW - 20 step step) {
                val p = bitmap.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)

                // Precise Carrom Pool Aim Arrow Head Color (Intense Red/Orange tip)
                if (r > 215 && g in 20..90 && b < 50) {
                    arrowHeadX += x
                    arrowHeadY += y
                    arrowCount++
                }
            }
        }

        // Require a solid cluster of arrow pixels to prevent false center-board detection
        if (arrowCount < 14) return null

        val headPt = PointF((arrowHeadX / arrowCount) * scaleX, (arrowHeadY / arrowCount) * scaleY)
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
