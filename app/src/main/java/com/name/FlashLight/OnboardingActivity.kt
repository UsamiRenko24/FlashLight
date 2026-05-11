package com.name.FlashLight

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.name.FlashLight.databinding.ActivityOnboardingBinding
import utils.AppLaunchManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val images = listOf(R.drawable.onboarding_1, R.drawable.onboarding_2, R.drawable.onboarding_3)
    private var startX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUI()

        val adapter = OnboardingAdapter(images) { position ->
            if (position == images.size - 1) {
                finishOnboarding()
            } else {
                binding.viewPager.currentItem = position + 1
            }
        }
        binding.viewPager.adapter = adapter

        // 监听滑动手势：在最后一页继续向左滑动（手指动作）以进入主页
        val recyclerView = binding.viewPager.getChildAt(0) as RecyclerView
        recyclerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                }
            }
            false
        }
    }

    private fun hideSystemUI() {

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller =
            WindowInsetsControllerCompat(
                window,
                window.decorView
            )

        controller.hide(
            WindowInsetsCompat.Type.systemBars()
        )

        controller.systemBarsBehavior =
            WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    private fun finishOnboarding() {
        AppLaunchManager.setNotFirstLaunch(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    inner class OnboardingAdapter(
        private val items: List<Int>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.imageView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.imageView.setImageResource(items[position])
            holder.itemView.setOnClickListener { onClick(position) }
        }

        override fun getItemCount(): Int = items.size
    }
}
