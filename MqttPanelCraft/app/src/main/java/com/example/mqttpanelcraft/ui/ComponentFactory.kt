package com.example.mqttpanelcraft.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.google.android.material.slider.Slider

object ComponentFactory {

    fun createComponentView(context: Context, tag: String, isEditMode: Boolean = false): View {
        val container = FrameLayout(context)
        container.setBackgroundResource(R.drawable.component_border)
        container.setPadding(8, 8, 8, 8)
        container.tag = tag
        
        val content: View = when(tag) {
            "BUTTON" -> Button(context).apply { 
                text = "BTN"
                isEnabled = isEditMode // Initially disabled if not edit mode? Or let logic handle it.
                // Click listeners are usually attached by the activity/binder
            }
            "TEXT" -> TextView(context).apply { 
                text = "Text" 
                gravity = Gravity.CENTER 
            }
            "SLIDER" -> Slider(context).apply { 
                valueFrom = 0f
                valueTo = 100f 
                value = 50f
            }
            "LED" -> View(context).apply { 
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.RED)
                }
            }
            "IMAGE" -> ImageView(context).apply { 
                setImageResource(android.R.drawable.ic_menu_gallery)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            "CAMERA" -> Button(context).apply { 
                text = "CAM"
            }
            else -> TextView(context).apply { text = tag }
        }
        
        container.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        
        if (tag == "IMAGE") {
            val closeBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                background = null
                val params = FrameLayout.LayoutParams(64, 64)
                params.gravity = Gravity.TOP or Gravity.END
                layoutParams = params
                this.tag = "CLEAR_BTN"
                visibility = if (isEditMode) View.VISIBLE else View.GONE
            }
            container.addView(closeBtn)
        }

        return container
    }
    
    // Estimate default size for Drag Shadow / Snapping
    fun getDefaultSize(context: Context, tag: String): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val w = when(tag) {
            "SLIDER" -> 200 * density
            "TEXT" -> 150 * density
            else -> 100 * density
        }
        val h = 100 * density
        return Pair(w.toInt(), h.toInt())
    }
}
