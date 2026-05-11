package com.name.FlashLight

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class StartupModeAdapter(context: Context, items: List<String>) :
    ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // 使用 super.getView 获取带有文本的 TextView
        val view = super.getView(position, convertView, parent) as TextView
        
        view.apply {
            // 确保显示的是传入的完整文本（例如：记住上次使用的功能）
            text = getItem(position)
            
            // 样式设置
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            
            // 重要：设置透明背景，避免覆盖 ListPopupWindow 的金色圆角边框
            setBackgroundColor(Color.TRANSPARENT)
            
            // 增加内边距，使列表项看起来更高级，点击区域更大
            setPadding(0, 35, 0, 35)
        }
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }
}
