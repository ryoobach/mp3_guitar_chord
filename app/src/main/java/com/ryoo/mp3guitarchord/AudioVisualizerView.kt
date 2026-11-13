package com.ryoo.mp3guitarchord

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max

class AudioVisualizerView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var levels = FloatArray(48)
    private val density = resources.displayMetrics.density

    fun setSpectrum(values: FloatArray) {
        levels = values.copyOf()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF101F32.toInt())
        val count = levels.size
        if (count == 0) return
        val gap = 2f * density
        val barWidth = max(1f, (width - gap * (count + 1)) / count)
        val centerY = height / 2f
        // Display-only scaling: keep FFT values and chord analysis untouched.
        val maxHeight = height * 0.43f * 0.55f

        for (i in 0 until count) {
            val level = levels[i].coerceIn(0.03f, 1f)
            val barHeight = max(2f * density, maxHeight * level)
            val left = gap + i * (barWidth + gap)
            val top = centerY - barHeight
            val bottom = centerY + barHeight
            val hue = when {
                i < count * 0.25f -> 8f + level * 28f
                i < count * 0.58f -> 95f + level * 45f
                else -> 190f + (i.toFloat() / count) * 105f
            }
            paint.color = android.graphics.Color.HSVToColor(
                255,
                floatArrayOf(hue.coerceIn(0f, 300f), 0.88f, (0.48f + level * 0.52f).coerceIn(0f, 1f))
            )
            val radius = barWidth / 2f
            canvas.drawRoundRect(RectF(left, top, left + barWidth, bottom), radius, radius, paint)
        }
    }
}
