package com.ryoo.mp3guitarchord

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class RotaryVolumeView(
    context: Context,
    initialValue: Int = 80,
    private val onVolumeChanged: (Int) -> Unit
) : View(context) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        color = 0xFF40566B.toInt()
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        color = 0xFFF5D66B.toInt()
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF203B52.toInt() }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF55D5FF.toInt(); strokeWidth = 3f; strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5D66B.toInt(); textAlign = Paint.Align.CENTER; textSize = 10f }
    var value: Int = initialValue.coerceIn(0, 100)
        private set
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    init { isClickable = true; contentDescription = "볼륨 조절" }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(MeasureSpec.getSuggestedMinimumWidth())
        val h = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(MeasureSpec.getSuggestedMinimumHeight())
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        centerX = width / 2f
        centerY = height * 0.46f
        radius = min(width * 0.27f, height * 0.34f).coerceAtLeast(8f)
        val oval = RectF(centerX-radius, centerY-radius, centerX+radius, centerY+radius)
        canvas.drawArc(oval, 135f, 270f, false, trackPaint)
        canvas.drawArc(oval, 135f, 270f * value / 100f, false, valuePaint)
        canvas.drawCircle(centerX, centerY, radius * 0.72f, knobPaint)
        val angle = Math.toRadians((135.0 + 270.0 * value / 100.0))
        canvas.drawLine(centerX, centerY, centerX + cos(angle).toFloat()*radius*0.58f, centerY + sin(angle).toFloat()*radius*0.58f, markerPaint)
        canvas.drawText("$value%", centerX, height - 2f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val degrees = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                val sweep = (degrees - 135f + 360f) % 360f
                val next = (sweep.coerceIn(0f, 270f) / 270f * 100f).toInt()
                if (next != value) {
                    value = next
                    onVolumeChanged(value)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> { performClick(); return true }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
