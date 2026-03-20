package com.name.FlashLight

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class StartupModeAdapter(context: Context, private val items: List<String>) :
    ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {

    // 控制未展开时显示的视图
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view as TextView

        // 根据当前选中的位置显示不同的文本
        textView.text = when (position) {
            0 -> "上次用"        // 显示简短版本
            1 -> "主页面"        // 显示简短版本
            2 -> "最常用"        // 显示简短版本
            else -> items[position]
        }
        textView.gravity = android.view.Gravity.CENTER
        textView.setTextColor(Color.WHITE)
        textView.setTextSize(12F)
        return view
    }

    // 控制下拉菜单中显示的视图
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val textView = view as TextView

        // 下拉菜单显示完整文本
        textView.text = items[position]
        textView.setTextColor(Color.WHITE)
        textView.setBackgroundColor(Color.parseColor("#2C2C2C"))

        return view
    }
}