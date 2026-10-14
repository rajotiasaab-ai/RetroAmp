package com.retroamp.musicplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Random
import kotlin.math.sin

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val NUM_BARS = 24
    }

    private val barLevels = FloatArray(NUM_BARS) { 0.08f }
    private val peakLevels = FloatArray(NUM_BARS) { 0.08f }
    private val barRect = RectF()
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var isPlaying = false
    private val random = Random()
    private var palette = intArrayOf(
        Color.parseColor("#00FF66"),
        Color.parseColor("#76FF03"),
        Color.parseColor("#FFEA00"),
        Color.parseColor("#FF3D00")
    )

    fun setPlaying(playing: Boolean) {
        this.isPlaying = playing
        invalidate()
    }

    fun setPalette(colors: IntArray) {
        if (colors.isNotEmpty()) {
            this.palette = colors
            invalidate()
        }
    }

    fun updateVisualizer() {
        if (isPlaying) {
            val now = System.currentTimeMillis()
            for (i in 0 until NUM_BARS) {
                val wave1 = sin((now / 140.0) + i * 0.55).toFloat()
                val wave2 = sin((now / 85.0) - i * 0.35).toFloat()
                val randomVal = random.nextFloat() * 0.28f
                val targetLevel = (0.35f + (wave1 + wave2) * 0.25f + randomVal).coerceIn(0.12f, 0.98f)

                // Smooth dampening towards target
                barLevels[i] = barLevels[i] * 0.35f + targetLevel * 0.65f

                if (barLevels[i] > peakLevels[i]) {
                    peakLevels[i] = barLevels[i]
                } else {
                    peakLevels[i] = (peakLevels[i] - 0.035f).coerceAtLeast(0.08f)
                }
            }
        } else {
            for (i in 0 until NUM_BARS) {
                barLevels[i] = (barLevels[i] * 0.82f).coerceAtLeast(0.05f)
                peakLevels[i] = (peakLevels[i] * 0.82f).coerceAtLeast(0.05f)
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val totalSpacing = 3f
        val barWidth = (w - (NUM_BARS + 1) * totalSpacing) / NUM_BARS

        for (i in 0 until NUM_BARS) {
            val left = totalSpacing + i * (barWidth + totalSpacing)
            val right = left + barWidth
            val barH = barLevels[i] * (h - 10f)
            val top = h - barH - 2f
            val bottom = h - 2f

            // Color gradient across the spectrum or based on height
            val colorIdx = ((barLevels[i] * palette.size).toInt()).coerceIn(0, palette.size - 1)
            barPaint.color = palette[colorIdx]

            barRect.set(left, top, right, bottom)
            canvas.drawRoundRect(barRect, 3f, 3f, barPaint)

            // Floating peak cap
            val peakTop = h - (peakLevels[i] * (h - 10f)) - 5f
            barRect.set(left, peakTop, right, peakTop + 3f)
            peakPaint.color = Color.WHITE
            canvas.drawRoundRect(barRect, 2f, 2f, peakPaint)
        }
    }
}
