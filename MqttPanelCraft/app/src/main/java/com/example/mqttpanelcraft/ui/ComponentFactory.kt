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
        val container = InterceptableFrameLayout(context)
        container.setBackgroundResource(R.drawable.component_border)
        container.setPadding(8, 8, 8, 8)
        container.tag = tag
        container.isEditMode = isEditMode // Pass mode
        
        // Setup content view
        val content: View = when(tag) {
            "BUTTON" -> Button(context).apply { 
                text = "BTN"
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                }
            }
            "TEXT" -> TextView(context).apply { 
                text = "Text" 
                gravity = Gravity.CENTER
            }
            "SLIDER" -> Slider(context).apply { 
                valueFrom = 0f
                valueTo = 100f 
                value = 50f
                stepSize = 1.0f // Fix: No decimals
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER // Fix: Center vertically
                }
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
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                }
            }
            "THERMOMETER" -> android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 50
                progressTintList = android.content.res.ColorStateList.valueOf(Color.RED)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                     gravity = Gravity.CENTER
                }
            }
            else -> TextView(context).apply { text = tag }
        }
        
        // Add content
        if (content.layoutParams == null) {
             container.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        } else {
             container.addView(content)
        }
        
        // Close/Clear Button (Special case)
        if (tag == "IMAGE") {
            val closeBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                background = null
                val params = FrameLayout.LayoutParams(64, 64)
                params.gravity = Gravity.TOP or Gravity.END
                layoutParams = params
                this.tag = "CLEAR_BTN"
                visibility = if (isEditMode) View.VISIBLE else View.GONE
                // Make sure this is ON TOP of the interceptor so it can be clicked even in edit mode
                elevation = 10f 
            }
            container.addView(closeBtn)
        }

        return container
    }
    
    // Estimate default size for Drag Shadow / Snapping
    fun getDefaultSize(context: Context, tag: String): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val w = when(tag) {
            "SLIDER", "THERMOMETER" -> 150 * density
            "TEXT" -> 150 * density
            "BUTTON", "CAMERA" -> 100 * density
            "LED" -> 75 * density
            else -> 100 * density
        }
        val h = if (tag == "LED") 75 * density else 100 * density
        return Pair(w.toInt(), h.toInt())
    }
}

// Custom FrameLayout to handle event interception in Edit Mode
class InterceptableFrameLayout(context: android.content.Context) : android.widget.FrameLayout(context) {
    var isEditMode: Boolean = false

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent?): Boolean {
        // In Edit Mode, we want to intercept all touches so the children (Button/Slider) don't get them.
        // This allows the OnTouchListener on THIS container (dragging) to work.
        if (isEditMode) {
             return true 
        }
        return super.onInterceptTouchEvent(ev)
    }
}
