package com.primegame.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlin.math.cos
import kotlin.math.sin

class AutoPlayService : AccessibilityService() {

    companion object {
        var instance: AutoPlayService? = null

        fun performShot(startX: Float, startY: Float, angleRad: Float, power: Float = 400f) {
            instance?.shoot(startX, startY, angleRad, power)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun shoot(startX: Float, startY: Float, angleRad: Float, power: Float) {
        // Aim এর ঠিক উল্টো দিকে স্ট্রাইকার টানতে হবে (Auto drag logic)
        val endX = startX - cos(angleRad) * power
        val endY = startY - sin(angleRad) * power

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        dispatchGesture(gesture, null, null)
    }
}
