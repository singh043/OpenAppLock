package com.openapplocktest.applock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class BackArrowView(
    context: Context
) : View(context) {

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth =
                2f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val d =
            resources.displayMetrics.density

        val offset =
            12f * d

        val tipX =
            offset + 8f * d

        val endX =
            offset + 15f * d

        val topY =
            offset + 5f * d

        val centerY =
            offset + 12f * d

        val bottomY =
            offset + 19f * d

        canvas.drawLine(
            endX,
            topY,
            tipX,
            centerY,
            paint
        )

        canvas.drawLine(
            tipX,
            centerY,
            endX,
            bottomY,
            paint
        )
    }
}
