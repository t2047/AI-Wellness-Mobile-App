package com.wellnessapp.ui.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.wellnessapp.R

/**
 * Circular wellness score ring used by the Analytics Dashboard.
 *
 * @author Xuhan Zhang
 */
class CircularScoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(RING_WIDTH_DP)
        color = 0xFFE6EDE7.toInt()
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(RING_WIDTH_DP)
        color = ContextCompat.getColor(context, R.color.primary)
    }

    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(32f)
        color = ContextCompat.getColor(context, R.color.primary)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val suffixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(13f)
        color = ContextCompat.getColor(context, R.color.textSecondary)
    }

    private val arcBounds = RectF()
    private var score = 0

    /**
     * Updates the displayed score, clamped to the 0-100 range.
     *
     * @author Xuhan Zhang
     */
    fun setScore(value: Int) {
        score = value.coerceIn(0, 100)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val requestedSize = suggestedMinimumWidth.coerceAtLeast(dp(DEFAULT_SIZE_DP).toInt())
        val width = resolveSize(requestedSize, widthMeasureSpec)
        val height = resolveSize(requestedSize, heightMeasureSpec)
        val size = width.coerceAtMost(height)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val strokeWidth = dp(RING_WIDTH_DP)
        val padding = strokeWidth / 2f + dp(4f)
        arcBounds.set(padding, padding, width - padding, height - padding)

        canvas.drawArc(arcBounds, START_ANGLE, SWEEP_ANGLE, false, trackPaint)
        canvas.drawArc(
            arcBounds,
            START_ANGLE,
            SWEEP_ANGLE * (score / 100f),
            false,
            progressPaint
        )

        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawText(score.toString(), centerX, centerY + dp(4f), scorePaint)
        canvas.drawText("/100", centerX, centerY + dp(25f), suffixPaint)
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun sp(value: Float): Float {
        return value * resources.displayMetrics.scaledDensity
    }

    companion object {
        private const val DEFAULT_SIZE_DP = 132f
        private const val RING_WIDTH_DP = 9f
        private const val START_ANGLE = -90f
        private const val SWEEP_ANGLE = 360f
    }
}
