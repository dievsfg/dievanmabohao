package com.diev.mabohao.ui.widget

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val GRID_SIZE = 3
        const val NODE_COUNT = GRID_SIZE * GRID_SIZE
    }

    enum class State {
        IDLE, INPUT, ERROR, SUCCESS
    }

    interface OnPatternListener {
        fun onPatternStart() {}
        fun onPatternComplete(pattern: List<Int>)
        fun onPatternTooShort(count: Int) {}
    }

    private var state: State = State.IDLE
    private var listener: OnPatternListener? = null

    // 节点坐标
    private val nodeCenters = Array(NODE_COUNT) { floatArrayOf(0f, 0f) }
    private var nodeRadius = 0f
    private var nodeSpacing = 0f
    private var gridOffsetX = 0f
    private var gridOffsetY = 0f

    // 选中的节点
    private val selectedNodes = mutableListOf<Int>()
    private val selectedSet = mutableSetOf<Int>()

    // 当前触摸位置
    private var touchX = 0f
    private var touchY = 0f
    private var isDrawing = false

    // 画笔 - 外圈
    private val outerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // 画笔 - 内心
    private val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // 画笔 - 连线
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val linePath = Path()

    // 颜色方案 - 深色模式
    private val darkColorNormal = 0xFF78909C.toInt()
    private val darkColorSelected = 0xFF42A5F5.toInt()
    private val darkColorError = 0xFFEF5350.toInt()
    private val darkColorSuccess = 0xFF66BB6A.toInt()

    // 颜色方案 - 浅色模式
    private val lightColorNormal = 0xFF90A4AE.toInt()
    private val lightColorSelected = 0xFF1976D2.toInt()
    private val lightColorError = 0xFFD32F2F.toInt()
    private val lightColorSuccess = 0xFF388E3C.toInt()

    // 当前使用的颜色
    private var colorNormal = darkColorNormal
    private var colorSelected = darkColorSelected
    private var colorError = darkColorError
    private var colorSuccess = darkColorSuccess

    init {
        updateColorsForTheme()
    }

    private fun updateColorsForTheme() {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            colorNormal = darkColorNormal
            colorSelected = darkColorSelected
            colorError = darkColorError
            colorSuccess = darkColorSuccess
        } else {
            colorNormal = lightColorNormal
            colorSelected = lightColorSelected
            colorError = lightColorError
            colorSuccess = lightColorSuccess
        }
    }

    fun setOnPatternListener(listener: OnPatternListener) {
        this.listener = listener
    }

    fun setState(newState: State) {
        state = newState
        invalidate()
    }

    fun clearPattern() {
        selectedNodes.clear()
        selectedSet.clear()
        isDrawing = false
        state = State.IDLE
        invalidate()
    }

    /** 错误时晃动动画 */
    fun shakeAnimation() {
        val animator = ObjectAnimator.ofFloat(this, "translationX", 0f, 15f, -15f, 10f, -10f, 5f, -5f, 0f)
        animator.duration = 500
        animator.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateNodePositions(w, h)
    }

    private fun calculateNodePositions(w: Int, h: Int) {
        val size = min(w, h).toFloat()
        nodeRadius = size / 14f
        nodeSpacing = size / 3.5f

        val gridSize = nodeSpacing * (GRID_SIZE - 1)
        gridOffsetX = (w - gridSize) / 2f
        gridOffsetY = (h - gridSize) / 2f

        for (i in 0 until NODE_COUNT) {
            val row = i / GRID_SIZE
            val col = i % GRID_SIZE
            nodeCenters[i][0] = gridOffsetX + col * nodeSpacing
            nodeCenters[i][1] = gridOffsetY + row * nodeSpacing
        }

        outerCirclePaint.strokeWidth = nodeRadius / 6f
        linePaint.strokeWidth = nodeRadius / 6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentColor = when (state) {
            State.IDLE -> colorNormal
            State.INPUT -> colorSelected
            State.ERROR -> colorError
            State.SUCCESS -> colorSuccess
        }

        // 绘制连线
        if (selectedNodes.size > 1) {
            linePaint.color = currentColor
            linePaint.alpha = 160
            linePath.reset()
            val first = nodeCenters[selectedNodes[0]]
            linePath.moveTo(first[0], first[1])
            for (i in 1 until selectedNodes.size) {
                val node = nodeCenters[selectedNodes[i]]
                linePath.lineTo(node[0], node[1])
            }
            if (isDrawing && state == State.INPUT) {
                linePath.lineTo(touchX, touchY)
            }
            canvas.drawPath(linePath, linePaint)
        } else if (selectedNodes.size == 1 && isDrawing && state == State.INPUT) {
            linePaint.color = currentColor
            linePaint.alpha = 160
            val first = nodeCenters[selectedNodes[0]]
            canvas.drawLine(first[0], first[1], touchX, touchY, linePaint)
        }

        // 绘制节点
        for (i in 0 until NODE_COUNT) {
            val cx = nodeCenters[i][0]
            val cy = nodeCenters[i][1]
            val isSelected = selectedSet.contains(i)

            if (isSelected) {
                // 选中节点：外圈变色 + 内心变色，大小不变
                outerCirclePaint.color = currentColor
                outerCirclePaint.alpha = 180
                canvas.drawCircle(cx, cy, nodeRadius, outerCirclePaint)

                innerCirclePaint.color = currentColor
                innerCirclePaint.alpha = 255
                canvas.drawCircle(cx, cy, nodeRadius * 0.35f, innerCirclePaint)
            } else {
                // 未选中节点
                outerCirclePaint.color = colorNormal
                outerCirclePaint.alpha = 120
                canvas.drawCircle(cx, cy, nodeRadius, outerCirclePaint)

                innerCirclePaint.color = colorNormal
                innerCirclePaint.alpha = 100
                canvas.drawCircle(cx, cy, nodeRadius * 0.35f, innerCirclePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state == State.ERROR || state == State.SUCCESS) {
            return true
        }

        touchX = event.x
        touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                clearPattern()
                state = State.INPUT
                isDrawing = true
                listener?.onPatternStart()
                checkNode(touchX, touchY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                checkNode(touchX, touchY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDrawing = false
                if (selectedNodes.size >= 4) {
                    listener?.onPatternComplete(selectedNodes.toList())
                } else if (selectedNodes.isNotEmpty()) {
                    listener?.onPatternTooShort(selectedNodes.size)
                    state = State.ERROR
                    vibrateError()
                    postDelayed({ clearPattern() }, 800)
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun checkNode(x: Float, y: Float) {
        for (i in 0 until NODE_COUNT) {
            if (selectedSet.contains(i)) continue
            val cx = nodeCenters[i][0]
            val cy = nodeCenters[i][1]
            val distance = hypot((x - cx).toDouble(), (y - cy).toDouble())
            if (distance <= nodeRadius * 1.5) {
                // 添加中间跳过的节点
                if (selectedNodes.isNotEmpty()) {
                    addIntermediateNodes(selectedNodes.last(), i)
                }
                selectedNodes.add(i)
                selectedSet.add(i)
                vibrateClick()
                break
            }
        }
    }

    private fun addIntermediateNodes(from: Int, to: Int) {
        val fromRow = from / GRID_SIZE
        val fromCol = from % GRID_SIZE
        val toRow = to / GRID_SIZE
        val toCol = to % GRID_SIZE

        val dRow = toRow - fromRow
        val dCol = toCol - fromCol

        if (Math.abs(dRow) <= 1 && Math.abs(dCol) <= 1) return

        val midRow = (fromRow + toRow) / 2
        val midCol = (fromCol + toCol) / 2
        val midNode = midRow * GRID_SIZE + midCol

        if (dRow % 2 == 0 && dCol % 2 == 0 && !selectedSet.contains(midNode)) {
            selectedNodes.add(midNode)
            selectedSet.add(midNode)
            vibrateClick()
        }
    }

    /** 节点选中时震动 - 轻触感 */
    private fun vibrateClick() {
        // 优先使用 View 的 haptic feedback（不需要VIBRATE权限，澎湃OS效果更好）
        val result = performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (!result) {
            // 回退到 Vibrator API
            try {
                val vibrator = getVibrator()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            } catch (_: Exception) {}
        }
    }

    /** 错误时震动 - 重触感 + 长时间 */
    fun vibrateError() {
        // 先执行 haptic feedback
        performHapticFeedback(HapticFeedbackConstants.REJECT)
        // 再叠加 Vibrator 震动
        try {
            val vibrator = getVibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80, 60, 80), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 80, 60, 80, 60, 80), -1)
            }
        } catch (_: Exception) {}
    }

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}