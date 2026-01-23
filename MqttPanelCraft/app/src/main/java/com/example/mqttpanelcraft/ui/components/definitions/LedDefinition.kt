package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object LedDefinition : IComponentDefinition {
    
    override val type = "LED"
    override val defaultSize = Size(80, 80) // Specific Default
    override val labelPrefix = "led"

    // Appearance
    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        
        val ledView = View(context).apply {
             background = GradientDrawable().apply {
                 shape = GradientDrawable.OVAL
                 setColor(Color.RED) // Default Off Color
             }
             // LED fills container usually or centered?
             // Factory used View(context).apply { ... } directly as content?
             // Let's make it fill match_parent with margin
             layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        container.addView(ledView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val ledView = container.getChildAt(0) ?: return
        
        data.props["color"]?.let { colorCode ->
             try {
                 val color = android.graphics.Color.parseColor(colorCode)
                 val bg = ledView.background as? GradientDrawable
                 bg?.setColor(color) // Set Initial/Base Color
             } catch(_: Exception) {}
        }
    }

    // Properties: None specific for now (maybe Color? But Color is common prop)
    override val propertiesLayoutId = 0 
    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
        // No specific properties yet
    }

    // Behavior
    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        val container = view as? FrameLayout ?: return
        val ledView = container.getChildAt(0) ?: return
        
        // Initial State? 
        // ComponentBehaviorManager logic was usually Update-only on message.
        // But maybe LED has click to toggle? (Usually not, LED is output)
        // Let's assume Output Only.
    }

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {
        val container = view as? FrameLayout ?: return
        // Child 0 is LED view, Child 1 might be Handle
        // Be careful looking up child.
        // Actually ComponentContainer adds content at 0.
        val ledView = container.getChildAt(0)
        if (ledView?.tag == "RESIZE_HANDLE") {
             // Ops, handle added first? 
             // ComponentContainer logic: 
             // container.addView(handle) -> Index 0
             // But my ButtonDefinition did: container.addView(button, 0) -> Index 0, Handle becomes Index 1.
             // So content is indeed at 0.
             return 
        }

        // Logic: Payload "1" -> Green, "0" -> Red?
        val bg = ledView.background as? GradientDrawable ?: return
        
        // Simple logic for now matching old behavior (presumed)
        if (payload == "1" || payload.equals("on", ignoreCase = true)) {
             bg.setColor(Color.GREEN)
        } else {
             bg.setColor(Color.RED)
        }
    }
}
