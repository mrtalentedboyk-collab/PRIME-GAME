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
        val aimAngle: Float,
        val isBotAiming: Boolean
    )

    fun processFrame(bitmap: Bitmap, screenWidth: Int, screenHeight: Int, isAutoPlayOn: Boolean): AnalysisResult? {
        val scaleX = screenWidth.toFloat() / bitmap.width.toFloat()
        val scaleY = screenHeight.toFloat() / bitmap.height.toFloat()
        
        // বোর্ডের সঠিক মাপ (স্ক্রিন অনুযায়ী)
        val boardMargin = 45f
        val boardSize = screenWidth - (boardMargin * 2)
        val boardTop = (screenHeight - screenWidth) / 2f + boardMargin
        val boardRect = RectF(boardMargin, boardTop, screenWidth - boardMargin, boardTop + boardSize)

        val pockets = listOf(
            PointF(boardRect.left + 45f, boardRect.top + 45f),
            PointF(boardRect.right - 45f, boardRect.top + 45f),
            PointF(boardRect.left + 45f, boardRect.bottom - 45f),
            PointF(boardRect.right - 45f, boardRect.bottom - 45f)
        )

        // গুটি (Coins) এবং স্ট্রাইকার স্ক্যান করা
        val coins = mutableListOf<PointF>()
        var strikerPt: PointF? = null

        val startY = (boardRect.top / scaleY).toInt()
        val endY = (boardRect.bottom / scaleY).toInt()
        val startX = (boardRect.left / scaleX).toInt()
        val endX = (boardRect.right / scaleX).toInt()

        val baselineY = boardRect.bottom - (boardSize * 0.15f) // স্ট্রাইকারের জায়গা
        
        for (y in startY until endY step 4) {
            for (x in startX until endX step 4) {
                val p = bitmap.getPixel(x, y)
                val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                
                val isWhite = r > 200 && g > 200 && b > 200
                val isBlack = r in 15..70 && g in 15..70 && b in 15..70
                
                if (isWhite || isBlack) {
                    val realX = x * scaleX
                    val realY = y * scaleY
                    
                    if (isWhite && abs(realY - baselineY) < 80f && realY > boardRect.centerY()) {
                        if (strikerPt == null) strikerPt = PointF(realX, realY)
                    } else {
                        if (coins.none { hypot(it.x - realX, it.y - realY) < 30f }) {
                            coins.add(PointF(realX, realY))
                        }
                    }
                }
            }
        }

        if (strikerPt == null) {
            strikerPt = PointF(boardRect.centerX(), baselineY)
        }

        var aimAngleRad = 0f
        var bestCoin: PointF? = null
        var bestPocket: PointF? = null
        var isBotAiming = false

        // প্রো-বট লজিক (নিজে গুটি খুঁজবে এবং টার্গেট করবে)
        if (isAutoPlayOn && coins.isNotEmpty()) {
            var bestScore = Float.MAX_VALUE
            for (coin in coins) {
                for (pocket in pockets) {
                    val anglePocketToCoin = atan2(coin.y - pocket.y, coin.x - pocket.x)
                    val ghostX = coin.x + cos(anglePocketToCoin) * 60f
                    val ghostY = coin.y + sin(anglePocketToCoin) * 60f
                    
                    val angleStrikerToGhost = atan2(ghostY - strikerPt.y, ghostX - strikerPt.x)
                    val cutAngle = abs(anglePocketToCoin - angleStrikerToGhost)
                    
                    if (cutAngle < bestScore) {
                        bestScore = cutAngle
                        bestCoin = coin
                        bestPocket = pocket
                        aimAngleRad = angleStrikerToGhost
                    }
                }
            }
            isBotAiming = true
        } else {
            // ম্যানুয়াল এইম (কাঠের রঙ ইগনোর করে শুধু অ্যারো ধরবে)
            var arrowX = 0f; var arrowY = 0f; var arrowCount = 0
            for (y in startY until endY step 3) {
                for (x in startX until endX step 3) {
                    val p = bitmap.getPixel(x, y)
                    val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                    // Bright Yellow/Orange Arrow
                    if (r > 220 && g > 150 && b < 50) {
                        arrowX += x; arrowY += y; arrowCount++
                    }
                }
            }
            if (arrowCount > 5) {
                val aX = (arrowX / arrowCount) * scaleX
                val aY = (arrowY / arrowCount) * scaleY
                aimAngleRad = atan2(aY - strikerPt.y, aX - strikerPt.x)
            } else return null
        }

        val strikerPath = mutableListOf<PointF>(strikerPt)
        val coinPath = mutableListOf<PointF>()
        var hitCoinPt: PointF? = null
        
        var rayX = strikerPt.x
        var rayY = strikerPt.y
        val stepX = cos(aimAngleRad) * 5f
        val stepY = sin(aimAngleRad) * 5f
        
        for (i in 0..250) {
            rayX += stepX
            rayY += stepY
            
            if (rayX <= boardRect.left || rayX >= boardRect.right || rayY <= boardRect.top || rayY >= boardRect.bottom) {
                strikerPath.add(PointF(rayX, rayY))
                break
            }
            
            var collided = false
            for (coin in coins) {
                if (hypot(rayX - coin.x, rayY - coin.y) < 60f) {
                    hitCoinPt = PointF(rayX, rayY)
                    strikerPath.add(hitCoinPt)
                    
                    coinPath.add(coin)
                    val targetPkt = bestPocket ?: pockets.minByOrNull { hypot(it.x - coin.x, it.y - coin.y) }
                    targetPkt?.let { coinPath.add(it) }
                    
                    collided = true
                    break
                }
            }
            if (collided) break
        }

        return AnalysisResult(strikerPath, coinPath, hitCoinPt, bestPocket, aimAngleRad, isBotAiming)
    }
}
