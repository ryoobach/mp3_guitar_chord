package com.ryoo.mp3guitarchord

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * Full-track spectrogram timeline.
 * Each FFT frame is recorded at its playback-time position, so analyzed
 * portions remain visible while the playhead continues moving.
 */
class TrackSpectrumView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val timeColumns = 320
    private val frequencyBands = 24
    private val history = Array(timeColumns) { FloatArray(frequencyBands) }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF101F32.toInt()
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5D66B.toInt()
        strokeWidth = 2f * density
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x334D718B
        strokeWidth = 1f * density
    }

    private var durationMs = 0
    private var positionMs = 0
    private var dragging = false
    private var onSeekPositionChanged: ((Int) -> Unit)? = null
    private var onSeekFinished: ((Int) -> Unit)? = null
    private var onSeekStarted: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "전체 재생 스펙트럼 및 탐색 바"
    }

    fun setOnSeekPositionChanged(listener: (Int) -> Unit) {
        onSeekPositionChanged = listener
    }

    fun setOnSeekFinished(listener: (Int) -> Unit) {
        onSeekFinished = listener
    }

    fun setOnSeekStarted(listener: () -> Unit) {
        onSeekStarted = listener
    }

    fun setDuration(duration: Int) {
        if (durationMs != duration.coerceAtLeast(0)) {
            durationMs = duration.coerceAtLeast(0)
            invalidate()
        }
    }

    fun setPosition(position: Int) {
        positionMs = position.coerceIn(0, durationMs.coerceAtLeast(0))
        invalidate()
    }

    fun reset() {
        for (column in history) column.fill(0f)
        positionMs = 0
        invalidate()
    }

    fun recordSpectrum(position: Int, duration: Int, fftLevels: FloatArray) {
        if (duration <= 0 || fftLevels.isEmpty()) return
        if (durationMs != duration) durationMs = duration
        val columnIndex = ((position.coerceIn(0, duration) / duration.toFloat()) * (timeColumns - 1))
            .toInt().coerceIn(0, timeColumns - 1)

        for (band in 0 until frequencyBands) {
            val from = band * fftLevels.size / frequencyBands
            val to = max(from + 1, (band + 1) * fftLevels.size / frequencyBands)
                .coerceAtMost(fftLevels.size)
            var peak = 0f
            for (i in from until to) peak = max(peak, fftLevels[i])
            // Keep the strongest frame seen in this time slice so the trace
            // does not disappear between FFT callbacks.
            history[columnIndex][band] = max(history[columnIndex][band], peak.coerceIn(0f, 1f))
        }
        positionMs = position.coerceIn(0, duration)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        if (width <= 0 || height <= 0) return

        val columnWidth = width.toFloat() / timeColumns
        val bandHeight = height.toFloat() / frequencyBands
        val gap = max(0.5f * density, 0.6f)

        for (x in 0 until timeColumns) {
            val left = x * columnWidth
            for (band in 0 until frequencyBands) {
                val level = history[x][band]
                if (level < 0.025f) continue
                val hue = (195f - band * 3.2f + level * 38f).coerceIn(145f, 225f)
                val saturation = 0.72f + level * 0.25f
                val brightness = (0.18f + level * 0.82f).coerceIn(0f, 1f)
                cellPaint.color = android.graphics.Color.HSVToColor(
                    (100 + level * 155).toInt().coerceIn(0, 255),
                    floatArrayOf(hue, saturation, brightness)
                )
                val top = height - (band + 1) * bandHeight
                canvas.drawRect(
                    left,
                    top + gap,
                    left + max(1f, columnWidth - gap),
                    top + bandHeight - gap,
                    cellPaint
                )
            }
        }

        // Light time grid for orientation.
        for (i in 1..3) {
            val x = width * i / 4f
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }

        if (durationMs > 0) {
            val cursorX = (positionMs.toFloat() / durationMs * width).coerceIn(0f, width.toFloat())
            canvas.drawLine(cursorX, 0f, cursorX, height.toFloat(), cursorPaint)
        }
    }

    private fun seekToTouch(x: Float) {
        if (durationMs <= 0 || width <= 0) return
        val fraction = (x / width).coerceIn(0f, 1f)
        val position = (durationMs * fraction).toInt()
        positionMs = position
        onSeekPositionChanged?.invoke(position)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (durationMs <= 0) return false
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                onSeekStarted?.invoke()
                seekToTouch(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) seekToTouch(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    seekToTouch(event.x)
                    onSeekFinished?.invoke(positionMs)
                }
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
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
