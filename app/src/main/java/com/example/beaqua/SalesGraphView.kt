package com.example.beaqua

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class SalesGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val salesPoints = mutableListOf<SalesPoint>()
    private var selectedIndex: Int = -1

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

    private val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val highlightOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D0077B6")
        style = Paint.Style.FILL
    }

    private val highlightInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0077B6")
        style = Paint.Style.FILL
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        style = Paint.Style.FILL
    }

    private val tooltipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val tooltipTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F8FAFC")
        textSize = dpToPx(12f)
        isFakeBoldText = true
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = dpToPx(11f)
    }

    private val tooltipValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        textSize = dpToPx(11f)
        isFakeBoldText = true
    }

    fun setSales(values: List<Double>) {
        setSalesPoints(values.map { SalesPoint(revenue = it, orderCount = 0, label = "") })
    }

    fun setSalesPoints(points: List<SalesPoint>) {
        salesPoints.clear()
        salesPoints.addAll(points)
        selectedIndex = -1
        invalidate()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_HOVER_MOVE || event.action == MotionEvent.ACTION_HOVER_ENTER) {
            updateSelectedIndex(event.x)
            return true
        } else if (event.action == MotionEvent.ACTION_HOVER_EXIT) {
            selectedIndex = -1
            invalidate()
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private var initialTouchIndex: Int = -1
    private var isDragging: Boolean = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                initialTouchIndex = selectedIndex
                isDragging = false
                updateSelectedIndex(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                updateSelectedIndex(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                if (!isDragging && selectedIndex == initialTouchIndex && initialTouchIndex != -1) {
                    selectedIndex = -1
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                selectedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateSelectedIndex(touchX: Float) {
        if (salesPoints.isEmpty()) return
        val left = paddingLeft + dpToPx(8f)
        val right = width - paddingRight - dpToPx(8f)
        val graphWidth = right - left
        val stepX = if (salesPoints.size == 1) graphWidth else graphWidth / (salesPoints.size - 1)

        var closestIndex = 0
        var minDistance = Float.MAX_VALUE

        salesPoints.indices.forEach { index ->
            val pointX = left + stepX * index
            val distance = abs(touchX - pointX)
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = index
            }
        }

        if (selectedIndex != closestIndex) {
            selectedIndex = closestIndex
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = paddingLeft + dpToPx(8f)
        val top = paddingTop + dpToPx(8f)
        val right = width - paddingRight - dpToPx(8f)
        val bottom = height - paddingBottom - dpToPx(8f)
        val graphWidth = right - left
        val graphHeight = bottom - top

        repeat(4) { index ->
            val y = top + (graphHeight / 3f) * index
            canvas.drawLine(left, y, right, y, axisPaint)
        }

        if (salesPoints.isEmpty()) return

        val maxValue = max(1.0, salesPoints.maxOfOrNull { it.revenue } ?: 1.0)
        val stepX = if (salesPoints.size == 1) graphWidth else graphWidth / (salesPoints.size - 1)
        val linePath = Path()
        val fillPath = Path()

        var selectedX = 0f
        var selectedY = 0f

        salesPoints.forEachIndexed { index, point ->
            val x = left + stepX * index
            val normalized = (point.revenue / maxValue).toFloat()
            val y = bottom - (graphHeight * normalized)

            if (index == selectedIndex) {
                selectedX = x
                selectedY = y
            }

            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            canvas.drawCircle(x, y, dpToPx(4f), pointPaint)
        }

        fillPath.lineTo(right, bottom)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        if (selectedIndex in salesPoints.indices) {
            drawSelectionAndTooltip(canvas, salesPoints[selectedIndex], selectedX, selectedY, top, bottom, left, right)
        }
    }

    private fun drawSelectionAndTooltip(
        canvas: Canvas,
        point: SalesPoint,
        x: Float,
        y: Float,
        top: Float,
        bottom: Float,
        left: Float,
        right: Float
    ) {
        canvas.drawLine(x, top, x, bottom, guideLinePaint)
        canvas.drawCircle(x, y, dpToPx(10f), highlightOuterPaint)
        canvas.drawCircle(x, y, dpToPx(5f), highlightInnerPaint)

        val title = point.label.ifBlank { "Summary" }
        val ordersLine = "Orders: ${point.orderCount}"
        val revenueLine = "Total: ${String.format(Locale.getDefault(), "₱%,.2f", point.revenue)}"

        val titleWidth = tooltipTitlePaint.measureText(title)
        val ordersWidth = tooltipTextPaint.measureText(ordersLine)
        val revenueWidth = tooltipValuePaint.measureText(revenueLine)

        val padding = dpToPx(8f)
        val lineSpacing = dpToPx(4f)
        val titleHeight = tooltipTitlePaint.textSize
        val textHeight = tooltipTextPaint.textSize

        val contentWidth = maxOf(titleWidth, maxOf(ordersWidth, revenueWidth))
        val boxWidth = contentWidth + padding * 2
        val boxHeight = titleHeight + textHeight * 2 + lineSpacing * 2 + padding * 2

        var boxLeft = x - boxWidth / 2f
        if (boxLeft < left) boxLeft = left
        if (boxLeft + boxWidth > right) boxLeft = right - boxWidth

        var boxTop = y - boxHeight - dpToPx(12f)
        if (boxTop < top) {
            boxTop = y + dpToPx(12f)
        }

        val boxRect = RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight)
        canvas.drawRoundRect(boxRect, dpToPx(8f), dpToPx(8f), tooltipBgPaint)
        canvas.drawRoundRect(boxRect, dpToPx(8f), dpToPx(8f), tooltipBorderPaint)

        var currentY = boxTop + padding + titleHeight
        canvas.drawText(title, boxLeft + padding, currentY, tooltipTitlePaint)

        currentY += lineSpacing + textHeight
        canvas.drawText(ordersLine, boxLeft + padding, currentY, tooltipTextPaint)

        currentY += lineSpacing + textHeight
        canvas.drawText(revenueLine, boxLeft + padding, currentY, tooltipValuePaint)
    }

    private fun dpToPx(dp: Float): Float = dp * context.resources.displayMetrics.density
}
