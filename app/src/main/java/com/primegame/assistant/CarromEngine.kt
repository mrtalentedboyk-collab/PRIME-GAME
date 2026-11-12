package com.primegame.assistant

import android.graphics.Bitmap
import android.graphics.PointF

class CarromEngine {
    init {
        System.loadLibrary("primegame")
    }

    data class Coin(val pos: PointF, val isMine: Boolean)
    
    data class AnalysisResult(
        val strikerTrajectory: List<PointF>,
        val allCoins: List<Coin>,
        val targetCoinPath: List<PointF>?,
        val targetPocket: PointF?,
        val aimAngle: Float,
        val isBotAiming: Boolean
    )

    private external fun processFrameNative(bitmap: Bitmap, width: Int, height: Int, isAutoPlayOn: Boolean): FloatArray?

    fun processFrame(bitmap: Bitmap, screenWidth: Int, screenHeight: Int, isAutoPlayOn: Boolean): AnalysisResult? {
        val data = processFrameNative(bitmap, screenWidth, screenHeight, isAutoPlayOn) ?: return null
        
        val strikerX = data[0]; val strikerY = data[1]
        val aimAngle = data[2]; val isBotAiming = data[3] > 0.5f
        val targetCoinX = data[4]; val targetCoinY = data[5]
        val targetPocketX = data[6]; val targetPocketY = data[7]
        val numCoins = data[8].toInt()

        val allCoins = mutableListOf<Coin>()
        var index = 9
        for (i in 0 until numCoins) {
            allCoins.add(Coin(PointF(data[index], data[index+1]), data[index+2] > 0.5f))
            index += 3
        }

        val strikerTrajectory = mutableListOf(PointF(strikerX, strikerY))
        val targetCoinPath = mutableListOf<PointF>()
        var targetPocketPt: PointF? = null

        if (isBotAiming && targetCoinX > 0) {
            // C++ থেকে পাওয়া অ্যাঙ্গেল অনুযায়ী লাইন টানা
            val hitPt = PointF(targetCoinX + Math.cos(aimAngle.toDouble()).toFloat() * 60f, targetCoinY + Math.sin(aimAngle.toDouble()).toFloat() * 60f)
            strikerTrajectory.add(hitPt)
            targetCoinPath.add(PointF(targetCoinX, targetCoinY))
            targetPocketPt = PointF(targetPocketX, targetPocketY)
            targetCoinPath.add(targetPocketPt)
        }

        return AnalysisResult(strikerTrajectory, allCoins, if (targetCoinPath.isNotEmpty()) targetCoinPath else null, targetPocketPt, aimAngle, isBotAiming)
    }
}
