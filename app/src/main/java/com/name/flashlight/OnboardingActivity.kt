package com.name.flashlight

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import com.name.flashlight.databinding.ActivityOnboardingBinding
import com.name.flashlight.utils.AppLaunchManager

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    // 三个独立页面
    private val pages = listOf(
        R.layout.onboarding1,
        R.layout.onboarding2,
        R.layout.onboarding3
    )

    private var startX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityOnboardingBinding.inflate(layoutInflater)

        setContentView(binding.root)

        hideSystemUI()

        val adapter =
            OnboardingAdapter(pages) { position ->

                // 最后一页进入主页
                if (position == pages.size - 1) {

                    finishOnboarding()

                } else {

                    binding.viewPager.currentItem =
                        position + 1
                }
            }

        binding.viewPager.adapter = adapter

        // 最后一页继续左滑进入主页
        val recyclerView =
            binding.viewPager.getChildAt(0)
                    as RecyclerView

        recyclerView.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    startX = event.x
                }

                MotionEvent.ACTION_UP -> {

                    val endX = event.x

                    val currentPage =
                        binding.viewPager.currentItem

                    // 左滑判断
                    if (
                        currentPage == pages.size - 1 &&
                        startX - endX > 150
                    ) {

                        finishOnboarding()
                    }
                }
            }

            false
        }
    }

    private fun hideSystemUI() {

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

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

        startActivity(
            Intent(this, MainActivity::class.java)
        )

        finish()
    }

    inner class OnboardingAdapter(
        private val items: List<Int>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

        inner class ViewHolder(
            view: View
        ) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolder {

            val view =
                LayoutInflater
                    .from(parent.context)
                    .inflate(
                        viewType,
                        parent,
                        false
                    )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int
        ) {

            // 底部点击区域
            val bottomClickArea =
                holder.itemView.findViewById<View>(
                    R.id.bottomClickArea
                )

            bottomClickArea.setOnClickListener {

                onClick(position)
            }
        }

        override fun getItemCount(): Int {

            return items.size
        }

        override fun getItemViewType(
            position: Int
        ): Int {

            return items[position]
        }
    }
}