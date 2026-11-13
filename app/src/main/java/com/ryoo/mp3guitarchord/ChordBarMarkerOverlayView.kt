package com.ryoo.mp3guitarchord

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * Draws estimated 4/4 bar boundaries over the chord history.
 * A marker is intentionally drawn above the chord text so it can bisect
 * a chord label when the estimated boundary falls during that chord.
 */
class ChordBarMarkerOverlayView(context: Context) : View(context) {

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(239, 68, 68)
        strokeWidth = 3.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    private var markerPositions: List<Float> = emptyList()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setMarkers(positions: List<Float>) {
        markerPositions = positions
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (height <= 0 || markerPositions.isEmpty()) return

        val inset = 1.5f * resources.displayMetrics.density
        val top = inset
        val bottom = height.toFloat() - inset

        markerPositions.forEach { x ->
            if (x >= 0f && x <= width.toFloat()) {
                canvas.drawLine(x, top, x, bottom, markerPaint)
            }
        }
    }
}
