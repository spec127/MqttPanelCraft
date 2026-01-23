package com.example.mqttpanelcraft.ui.components

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import com.example.mqttpanelcraft.R

object ComponentContainer {
    
    fun createEndpoint(context: Context, tag: String, isEditMode: Boolean): FrameLayout {
        val container = InterceptableFrameLayout(context)
        container.setBackgroundResource(R.drawable.component_border)
        container.setPadding(8, 8, 8, 8)
        container.tag = tag // Important for Renderer
        container.isEditMode = isEditMode

        // Add Resize Handle (Default for all components)
        val handle = View(context).apply {
            this.tag = "RESIZE_HANDLE"
            layoutParams = FrameLayout.LayoutParams(40, 40).apply {
                 gravity = Gravity.BOTTOM or Gravity.END
            }
            background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
                paint.color = android.graphics.Color.parseColor("#6200EE")
            }
            elevation = 20f
            visibility = View.GONE 
        }
        container.addView(handle)

        // Close/Clear Button (Special case for Image, or maybe generic later)
        if (tag == "IMAGE") {
             val closeBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                background = null
                val params = FrameLayout.LayoutParams(64, 64)
                params.gravity = Gravity.TOP or Gravity.END
                layoutParams = params
                this.tag = "CLEAR_BTN"
                visibility = if (isEditMode) View.VISIBLE else View.GONE
                elevation = 10f 
            }
            container.addView(closeBtn)
        }

        return container
    }
}


