package com.retroamp.musicplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CassetteDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isPlaying = false
    private var rotationAngle = 0f
    private var progressRatio = 0f // 0.0 to 1.0 (left reel empty to right reel full)

    private var accentColor = Color.parseColor("#00FF66")
    private var secondaryColor = Color.parseColor("#FFD700")
    private var chassisColor = Color.parseColor("#15161C")
    private var windowBgColor = Color.parseColor("#090A0D")

    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val reelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val reelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1C1512") // Dark brown magnetic tape
    }
    private val spokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val tapeWindowRect = RectF()

    fun setPlaying(playing: Boolean) {
        if (this.isPlaying != playing) {
            this.isPlaying = playing
            if (playing) {
                postInvalidateOnAnimation()
            }
        }
    }

    fun setProgress(progress: Float) {
        this.progressRatio = progress.coerceIn(0f, 1f)
        invalidate()
    }

    fun setThemeColors(accent: Int, secondary: Int, chassis: Int, windowBg: Int) {
        this.accentColor = accent
        this.secondaryColor = secondary
        this.chassisColor = chassis
        this.windowBgColor = windowBg
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Draw Cassette Outer Bezel / Chassis
        chassisPaint.color = chassisColor
        canvas.drawRoundRect(0f, 0f, w, h, 14f, 14f, chassisPaint)

        borderPaint.color = Color.argb(60, 255, 255, 255)
        borderPaint.strokeWidth = 2.5f
        canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, 14f, 14f, borderPaint)

        // Subtle accent line on top edge
        borderPaint.color = accentColor
        borderPaint.strokeWidth = 3f
        canvas.drawLine(16f, 2f, w - 16f, 2f, borderPaint)

        // 2. Center Tape Observation Window
        val windowMarginH = w * 0.12f
        val windowMarginV = h * 0.14f
        tapeWindowRect.set(windowMarginH, windowMarginV, w - windowMarginH, h - windowMarginV)

        chassisPaint.color = windowBgColor
        canvas.drawRoundRect(tapeWindowRect, 10f, 10f, chassisPaint)

        borderPaint.color = Color.argb(100, 255, 255, 255)
        borderPaint.strokeWidth = 1.8f
        canvas.drawRoundRect(tapeWindowRect, 10f, 10f, borderPaint)

        // 3. Spool Centers
        val centerY = h / 2f
        val leftCenterX = tapeWindowRect.left + (tapeWindowRect.width() * 0.28f)
        val rightCenterX = tapeWindowRect.right - (tapeWindowRect.width() * 0.28f)
        val maxTapeRadius = min(tapeWindowRect.height() * 0.40f, tapeWindowRect.width() * 0.22f)
        val hubRadius = maxTapeRadius * 0.45f

        // 4. Draw Magnetic Tape Thickness on Spools (Left unspools, Right spools up)
        val leftTapeRadius = hubRadius + (maxTapeRadius - hubRadius) * (1f - progressRatio)
        val rightTapeRadius = hubRadius + (maxTapeRadius - hubRadius) * progressRatio

        // Left Tape Pack
        tapePaint.color = Color.parseColor("#251C17")
        canvas.drawCircle(leftCenterX, centerY, leftTapeRadius, tapePaint)

        // Right Tape Pack
        canvas.drawCircle(rightCenterX, centerY, rightTapeRadius, tapePaint)

        // Horizontal magnetic tape ribbon connecting the reels across the bottom
        borderPaint.color = Color.parseColor("#38271C")
        borderPaint.strokeWidth = 5f
        val tapeBottomY = tapeWindowRect.bottom - 8f
        canvas.drawLine(leftCenterX, tapeBottomY, rightCenterX, tapeBottomY, borderPaint)

        // Magnetic head indicator at center bottom
        chassisPaint.color = accentColor
        val headX = (leftCenterX + rightCenterX) / 2f
        canvas.drawRect(headX - 10f, tapeBottomY - 4f, headX + 10f, tapeBottomY + 4f, chassisPaint)

        // 5. Draw White Plastic Hubs and Rotating Teeth (Left and Right)
        drawReelHub(canvas, leftCenterX, centerY, hubRadius, rotationAngle)
        drawReelHub(canvas, rightCenterX, centerY, hubRadius, rotationAngle)

        // 6. Center Millimeter Gauge / Ruler
        val gaugeStartX = leftCenterX + hubRadius + 12f
        val gaugeEndX = rightCenterX - hubRadius - 12f
        if (gaugeEndX > gaugeStartX) {
            borderPaint.color = Color.argb(80, 255, 255, 255)
            borderPaint.strokeWidth = 1.5f
            val numTicks = 6
            val step = (gaugeEndX - gaugeStartX) / numTicks
            for (i in 0..numTicks) {
                val tx = gaugeStartX + i * step
                val tickH = if (i == 0 || i == numTicks || i == numTicks / 2) 12f else 6f
                canvas.drawLine(tx, centerY - tickH / 2f, tx, centerY + tickH / 2f, borderPaint)
            }
        }

        // 7. Update rotation animation if playing
        if (isPlaying) {
            rotationAngle = (rotationAngle + 3.5f) % 360f
            postInvalidateOnAnimation()
        }
    }

    private fun drawReelHub(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float) {
        // Hub body
        reelPaint.color = Color.parseColor("#E0E3EB")
        canvas.drawCircle(cx, cy, radius, reelPaint)

        // Hub outer rim
        reelStrokePaint.color = Color.parseColor("#7A7E8D")
        reelStrokePaint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, radius, reelStrokePaint)

        // Center spindle hole
        reelPaint.color = windowBgColor
        canvas.drawCircle(cx, cy, radius * 0.38f, reelPaint)

        // 3 Drive Teeth / Sprockets
        spokePaint.color = accentColor
        spokePaint.strokeWidth = 3.5f

        val spokeInnerR = radius * 0.38f
        val spokeOuterR = radius * 0.88f

        for (i in 0..2) {
            val rad = Math.toRadians((angle + i * 120).toDouble())
            val sx = cx + (spokeInnerR * cos(rad)).toFloat()
            val sy = cy + (spokeInnerR * sin(rad)).toFloat()
            val ex = cx + (spokeOuterR * cos(rad)).toFloat()
            val ey = cy + (spokeOuterR * sin(rad)).toFloat()
            canvas.drawLine(sx, sy, ex, ey, spokePaint)
        }
    }
}

