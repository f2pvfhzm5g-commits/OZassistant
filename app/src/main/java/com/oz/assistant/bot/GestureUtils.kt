package com.oz.assistant.bot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path

object GestureUtils {

    fun tap(service: AccessibilityService, x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        service.dispatchGesture(gesture, null, null)
    }
}
