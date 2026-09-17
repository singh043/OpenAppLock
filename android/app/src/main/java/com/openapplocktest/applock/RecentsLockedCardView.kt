package com.openapplocktest.applock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.min

/**
 * Temporary Android 14 Recents-window proof view.
 *
 * It renders only the locked-card visual. It is intentionally not the
 * existing credential overlay, so we can first verify that
 * attachAccessibilityOverlayToWindow() actually appears above Samsung
 * One UI Home Recents on this device.
 */
class RecentsLockedCardView(
    context: Context,
    private val packageName: String
) : View(context) {

    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(8, 8, 8)
            style = Paint.Style.FILL
        }

    private val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 34, 34)
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
        }

    private val titlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sp(22f)
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

    private val subPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(170, 170, 170)
            textSize = sp(15f)
            textAlign = Paint.Align.CENTER
        }

    private val lockPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(5).toFloat()
        }

    private val icon: Drawable? =
        try {
            context.packageManager
                .getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = dp(26).toFloat()
        val left = 0f
        val top = 0f
        val right = width.toFloat()
        val bottom = height.toFloat()

        canvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            radius,
            radius,
            fillPaint
        )

        canvas.drawRoundRect(
            left + dp(1).toFloat(),
            top + dp(1).toFloat(),
            right - dp(1).toFloat(),
            bottom - dp(1).toFloat(),
            radius,
            radius,
            borderPaint
        )

        val centerX = width / 2f
        val centerY = height / 2f

        val iconSize = min(dp(96), width / 4)
        val iconLeft = (centerX - iconSize / 2f).toInt()
        val iconTop = (centerY - dp(170)).toInt()

        icon?.setBounds(
            iconLeft,
            iconTop,
            iconLeft + iconSize,
            iconTop + iconSize
        )
        icon?.draw(canvas)

        // Simple lock symbol below the app icon.
        val lockWidth = dp(46)
        val lockHeight = dp(38)
        val lockLeft = centerX - lockWidth / 2f
        val lockTop = (iconTop + iconSize + dp(24)).toFloat()
        val lockRight = centerX + lockWidth / 2f
        val lockBottom = (lockTop.toInt() + lockHeight).toFloat()

        canvas.drawRoundRect(
            lockLeft,
            lockTop,
            lockRight,
            lockBottom,
            dp(8).toFloat(),
            dp(8).toFloat(),
            lockPaint
        )

        canvas.drawArc(
            centerX - dp(18).toFloat(),
            lockTop - dp(24).toFloat(),
            centerX + dp(18).toFloat(),
            lockTop + dp(18).toFloat(),
            180f,
            180f,
            false,
            lockPaint
        )

        canvas.drawText(
            "App Locked",
            centerX,
            lockBottom + dp(55).toFloat(),
            titlePaint
        )

        canvas.drawText(
            "Unlock to view",
            centerX,
            lockBottom + dp(82).toFloat(),
            subPaint
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun sp(value: Float): Float =
        value * resources.displayMetrics.scaledDensity
}
