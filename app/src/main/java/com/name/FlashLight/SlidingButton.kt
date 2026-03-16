package com.name.FlashLight

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

class SlidingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 状态
    var isChecked: Boolean
        get() = _isChecked
        set(value) {
            if (_isChecked != value) {
                _isChecked = value
                animateToState(value)
                listener?.onStateChanged(value)
                invalidate()
            }
        }
    fun setCheckedSilently(checked: Boolean) {
        if (_isChecked != checked) {
            _isChecked = checked
            animateToState(checked)
            invalidate()
        }
    }
    // 监听器
    private var listener: OnStateChangedListener? = null
    private var _isChecked: Boolean = false

    // 动画
    private var animator: ValueAnimator? = null
    private var currentPosition = 0f  // 0=关, 1=开

    // 颜色
    private var trackOffColor = Color.parseColor("#E0E0E0")  // 关闭时轨道颜色
    private var trackOnColor = Color.parseColor("#4CAF50")   // 开启时轨道颜色
    private var thumbColor = Color.WHITE                      // 滑块颜色

    // 尺寸
    private var trackWidth = 0f
    private var trackHeight = 0f
    private var thumbSize = 0f
    private var cornerRadius = 0f

    // 画笔
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = thumbColor
        setShadowLayer(4f, 0f, 2f, Color.parseColor("#33000000"))
    }

    // 阴影层（用于清除阴影）
    private val shadowLayer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
    }

    init {
        // 设置点击事件
        setOnClickListener {
            toggle()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 计算尺寸
        trackWidth = w.toFloat()
        trackHeight = h.toFloat()
        thumbSize = trackHeight * 0.8f
        cornerRadius = trackHeight / 2

        // 设置初始位置
        currentPosition = if (isChecked) 1f else 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 计算轨道颜色（根据当前进度插值）
        val trackColor = interpolateColor(trackOffColor, trackOnColor, currentPosition)
        trackPaint.color = trackColor

        // 绘制轨道
        canvas.drawRoundRect(
            0f, 0f, trackWidth, trackHeight,
            cornerRadius, cornerRadius,
            trackPaint
        )

        // 计算滑块位置
        val maxThumbX = trackWidth - thumbSize
        val thumbX = maxThumbX * currentPosition
        val thumbY = (trackHeight - thumbSize) / 2

        // 清除阴影区域（避免阴影重叠）
        canvas.drawRoundRect(
            thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize,
            thumbSize / 2, thumbSize / 2,
            shadowLayer
        )

        // 绘制滑块
        canvas.drawCircle(
            thumbX + thumbSize / 2,
            thumbY + thumbSize / 2,
            thumbSize / 2,
            thumbPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 按下时稍微缩小滑块（反馈效果）
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                return true
            }
            MotionEvent.ACTION_UP -> {
                // 松开时恢复大小并切换状态
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // 切换状态
    fun toggle() {
        isChecked = !isChecked
    }

    // 带动画切换状态
    private fun animateToState(checked: Boolean) {
        animator?.cancel()

        val target = if (checked) 1f else 0f
        animator = ValueAnimator.ofFloat(currentPosition, target).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentPosition = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // 设置状态变化监听

    // 设置监听器
    fun setOnStateChangedListener(listener: (Boolean) -> Unit) {
        this.listener = object : OnStateChangedListener {
            override fun onStateChanged(isChecked: Boolean) {
                listener(isChecked)
            }
        }
    }


    // 颜色插值函数
    private fun interpolateColor(from: Int, to: Int, fraction: Float): Int {
        val red = (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction).toInt()
        val green = (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction).toInt()
        val blue = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction).toInt()
        return Color.rgb(red, green, blue)
    }

    // 监听器接口
    interface OnStateChangedListener {
        fun onStateChanged(isChecked: Boolean)
    }
}