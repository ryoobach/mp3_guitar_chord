package com.ryoo.mp3guitarchord

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * Horizontal volume slider styled like the track progress bar.
 * Drag the circular thumb left or right to set playback volume.
 */
class HorizontalVolumeBarView(
    context: Context,
    initialValue: Int = 80,
    private val onVolumeChanged: (Int) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val trackHeight = 5f * density
    private val thumbRadius = 8f * density
    private val horizontalInset = 12f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF40566B.toInt()
        strokeWidth = trackHeight
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF55D5FF.toInt()
        strokeWidth = trackHeight
        strokeCap = Paint.Cap.ROUND
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5D66B.toInt()
        style = Paint.Style.FILL
    }
    private val thumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF55D5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    var value: Int = initialValue.coerceIn(0, 100)
        private set

    private var isDragging = false

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "가로형 볼륨 조절"
    }

    private fun startX(): Float = horizontalInset + thumbRadius
    private fun endX(): Float = max(startX() + 1f, width - horizontalInset - thumbRadius)
    private fun centerY(): Float = height / 2f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = startX()
        val right = endX()
        val y = centerY()
        val thumbX = left + (right - left) * value / 100f

        canvas.drawLine(left, y, right, y, trackPaint)
        if (thumbX > left) {
            canvas.drawLine(left, y, thumbX, y, progressPaint)
        }
        canvas.drawCircle(thumbX, y, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbX, y, thumbRadius, thumbBorderPaint)
    }

    private fun updateFromTouch(x: Float) {
        val fraction = ((x - startX()) / (endX() - startX())).coerceIn(0f, 1f)
        val nextValue = (fraction * 100f).toInt()
        if (nextValue != value) {
            value = nextValue
            onVolumeChanged(value)
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) updateFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) updateFromTouch(event.x)
                isDragging = false
                performClick()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
