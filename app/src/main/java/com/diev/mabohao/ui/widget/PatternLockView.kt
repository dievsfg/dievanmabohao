package com.diev.mabohao.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class PatternLockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var dotRadius = 24f
    private var selectedDotRadius = 36f
    
    private var normalColor = Color.parseColor("#BDBDBD")
    private var selectedColor = Color.parseColor("#1976D2")
    private var errorColor = Color.parseColor("#F44336")

    private val dots = Array(9) { Dot(it) }
    private val selectedDots = mutableListOf<Dot>()
    
    private var currentX = -1f
    private var currentY = -1f
    private var isDrawing = false
    private var isError = false

    private val linePath = Path()

    var onPatternListener: OnPatternListener? = null

    interface OnPatternListener {
        fun onComplete(pattern: List<Int>)
        fun onCleared()
    }

    class Dot(val id: Int) {
        var cx = 0f
        var cy = 0f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = minOf(w, h)
        val padding = size / 6f
        val spacing = (size - 2 * padding) / 2f
        
        val startX = (w - size) / 2f + padding
        val startY = (h - size) / 2f + padding

        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3
            dots[i].cx = startX + col * spacing
            dots[i].cy = startY + row * spacing
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val color = if (isError) errorColor else selectedColor
        linePaint.color = color

        // Draw connecting lines
        if (selectedDots.isNotEmpty()) {
            linePath.reset()
            linePath.moveTo(selectedDots[0].cx, selectedDots[0].cy)
            for (i in 1 until selectedDots.size) {
                linePath.lineTo(selectedDots[i].cx, selectedDots[i].cy)
            }
            if (isDrawing) {
                linePath.lineTo(currentX, currentY)
            }
            canvas.drawPath(linePath, linePaint)
        }

        // Draw dots
        for (dot in dots) {
            if (selectedDots.contains(dot)) {
                dotPaint.color = color
                canvas.drawCircle(dot.cx, dot.cy, selectedDotRadius, dotPaint)
                // Draw inner circle
                dotPaint.color = Color.WHITE
                canvas.drawCircle(dot.cx, dot.cy, dotRadius * 0.4f, dotPaint)
            } else {
                dotPaint.color = normalColor
                canvas.drawCircle(dot.cx, dot.cy, dotRadius, dotPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        currentX = event.x
        currentY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                resetPattern()
                isDrawing = true
                detectDot(currentX, currentY)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    detectDot(currentX, currentY)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDrawing) {
                    isDrawing = false
                    invalidate()
                    if (selectedDots.isNotEmpty()) {
                        onPatternListener?.onComplete(selectedDots.map { it.id })
                    }
                }
            }
        }
        return true
    }

    private fun detectDot(x: Float, y: Float) {
        val hitRadius = selectedDotRadius * 2f
        for (dot in dots) {
            if (!selectedDots.contains(dot)) {
                val distance = hypot((dot.cx - x).toDouble(), (dot.cy - y).toDouble()).toFloat()
                if (distance <= hitRadius) {
                    selectedDots.add(dot)
                    // Haptic feedback could be added here
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    break
                }
            }
        }
    }

    fun resetPattern() {
        selectedDots.clear()
        isError = false
        isDrawing = false
        invalidate()
        onPatternListener?.onCleared()
    }

    fun setErrorState() {
        isError = true
        invalidate()
    }
}