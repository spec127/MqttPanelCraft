package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object ButtonDefinition : IComponentDefinition {
    
    // 1. Identity
    override val type = "BUTTON"
    override val defaultSize = Size(120, 60)
    override val labelPrefix = "btn"

    // 2. Factory (Appearance)
    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        
        val button = androidx.appcompat.widget.AppCompatButton(context).apply { 
            text = "BTN"
            // Flat Design: No Shadow
            stateListAnimator = null
            elevation = 0f
            textSize = 14f
            isAllCaps = false
            
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
                setMargins(8, 8, 8, 8)
            }
        }
        // Add content at index 0 (behind handle)
        container.addView(button, 0)
        
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val button = findButtonIn(container) ?: return
        
        // 1. Color (Background)
        val colorHex = data.props["color"] ?: "#6200EE" // Default Purple
        val color = try {
            android.graphics.Color.parseColor(colorHex)
        } catch(e: Exception) {
            android.graphics.Color.LTGRAY
        }
        
        // Create Drawable for Circular Design
        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
             shape = android.graphics.drawable.GradientDrawable.OVAL
             setColor(color)
        }
        button.background = bgDrawable
        
        // 2. Text (Label)
        val btnText = data.props["text"] ?: data.label
        button.text = btnText
        // Auto-contrast text color
        if (androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5) {
            button.setTextColor(android.graphics.Color.BLACK)
            button.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
        } else {
            button.setTextColor(android.graphics.Color.WHITE)
            button.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }
        
        // 3. Icon
        val iconKey = data.props["icon"]
        val iconRes = when(iconKey) {
            "plus" -> android.R.drawable.ic_input_add
            "delete" -> android.R.drawable.ic_delete
            "send" -> android.R.drawable.ic_menu_send
            "edit" -> android.R.drawable.ic_menu_edit
            "info" -> android.R.drawable.ic_dialog_info
            "mic" -> android.R.drawable.btn_star 
            else -> 0
        }
        
        // Set Icon (Left)
        if (iconRes != 0) {
            val d = androidx.core.content.ContextCompat.getDrawable(button.context, iconRes)
            button.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null)
            button.compoundDrawablePadding = 8
        } else {
            button.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        }
        
        // 4. Pressed State (Simple Darken)
        button.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Darken
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(color, hsv)
                    hsv[2] *= 0.8f // Darken value
                    val darkColor = android.graphics.Color.HSVToColor(hsv)
                    (v.background as? android.graphics.drawable.GradientDrawable)?.setColor(darkColor)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    // Restore
                    (v.background as? android.graphics.drawable.GradientDrawable)?.setColor(color)
                    // If UP, perform click handled by click listener
                }
            }
            false // Process click
        }
    }

    // 3. Properties (Binder) - Deprecated, logic moved to PropertiesSheetManager
    override val propertiesLayoutId = 0
    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {}

    // 4. Runtime Behavior
    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        val container = view as? FrameLayout ?: return
        val button = findButtonIn(container) ?: return
        
        // Ensure onClick fires (setOnTouchListener returns false, so onClick works)
        button.setOnClickListener {
             // Logic
             val topic = data.topicConfig
             if (topic.isNotEmpty()) {
                 val payload = data.props["payload"] ?: "1"
                 sendMqtt(topic, payload)
             }
        }
    }

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {}

    private fun findButtonIn(container: FrameLayout): androidx.appcompat.widget.AppCompatButton? {
        for (i in 0 until container.childCount) {
             val child = container.getChildAt(i)
             if (child is androidx.appcompat.widget.AppCompatButton) return child
             // Fallback for old buttons if mismatch? Recreate in factory if type changes.
             // But for now, we assume fresh creation or compatible.
             if (child is Button) return child as? androidx.appcompat.widget.AppCompatButton
        }
        return null
    }
}
