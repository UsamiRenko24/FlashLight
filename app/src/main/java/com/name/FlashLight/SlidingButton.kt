package com.name.FlashLight

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 纯净的滑动按钮 (Industrial Grade)
 * 职责：只负责渲染 UI 和处理触摸事件，不持有任何业务逻辑。
 */
class SlidingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var _isChecked: Boolean = false
    private var listener: OnStateChangedListener? = null

    var isChecked: Boolean
        get() = _isChecked
        set(value) {
            if (_isChecked != value) {
                _isChecked = value
                animateToState(value)
                // 核心变化：只负责通知监听者，不再自己去震动或存数据
                listener?.onStateChanged(value)
                invalidate()
            }
        }

    /**
     * 静默设置状态（不触发监听回调）
     * 用于从数据库初始化 UI，防止产生循环触发和不必要的反馈
     */
    fun setCheckedSilently(checked: Boolean) {
        if (_isChecked != checked) {
            _isChecked = checked
            animateToState(checked)
            invalidate()
        }
    }

    // 绘制逻辑、动画逻辑保持不变（省略部分代码以示简洁...）
    private var animator: ValueAnimator? = null
    private var currentPosition = 0f
    private var trackOffColor = Color.parseColor("#E0E0E0")
    private var trackOnColor = Color.parseColor("#4786EF") // 使用项目主题色
    private var thumbColor = Color.WHITE
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = thumbColor }

    init {
        setOnClickListener { toggle() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val trackColor = interpolateColor(trackOffColor, trackOnColor, currentPosition)
        trackPaint.color = trackColor
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, trackPaint)
        
        val thumbSize = height * 0.8f
        val thumbX = (width - thumbSize) * currentPosition + thumbSize / 2
        canvas.drawCircle(thumbX, height / 2f, thumbSize / 2, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                performClick()
            }
        }
        return true
    }

    fun toggle() { isChecked = !isChecked }

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

    fun setOnStateChangedListener(listener: (Boolean) -> Unit) {
        this.listener = object : OnStateChangedListener {
            override fun onStateChanged(isChecked: Boolean) { listener(isChecked) }
        }
    }

    private fun interpolateColor(from: Int, to: Int, fraction: Float): Int {
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction).toInt()
        return Color.rgb(r, g, b)
    }

    interface OnStateChangedListener { fun onStateChanged(isChecked: Boolean) }
}