package com.example.beaqua

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class SalesGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val salesValues = mutableListOf<Double>()

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#330096C7")
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0077B6")
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#023E8A")
        style = Paint.Style.FILL
    }

    fun setSales(values: List<Double>) {
        salesValues.clear()
        salesValues.addAll(values)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = paddingLeft + 8f
        val top = paddingTop + 8f
        val right = width - paddingRight - 8f
        val bottom = height - paddingBottom - 8f
        val graphWidth = right - left
        val graphHeight = bottom - top

        repeat(4) { index ->
            val y = top + (graphHeight / 3f) * index
            canvas.drawLine(left, y, right, y, axisPaint)
        }

        if (salesValues.isEmpty()) return

        val maxValue = max(1.0, salesValues.maxOrNull() ?: 1.0)
        val stepX = if (salesValues.size == 1) graphWidth else graphWidth / (salesValues.size - 1)
        val linePath = Path()
        val fillPath = Path()

        salesValues.forEachIndexed { index, value ->
            val x = left + stepX * index
            val normalized = (value / maxValue).toFloat()
            val y = bottom - (graphHeight * normalized)

            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            canvas.drawCircle(x, y, 5f, pointPaint)
        }

        fillPath.lineTo(right, bottom)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }
}
