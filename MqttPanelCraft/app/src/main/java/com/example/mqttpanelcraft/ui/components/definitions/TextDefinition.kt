package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object TextDefinition : IComponentDefinition {
    
    override val type = "TEXT"
    override val defaultSize = Size(160, 100)
    override val labelPrefix = "txt"

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        val tv = TextView(context).apply {
            text = "Text"
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        container.addView(tv, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val tv = container.getChildAt(0) as? TextView ?: return
        
        data.props["color"]?.let { colorCode ->
             try {
                 val color = android.graphics.Color.parseColor(colorCode)
                 tv.setTextColor(color) // Text Color instead of Background
             } catch(_: Exception) {}
        }
    }

    override val propertiesLayoutId = 0 // Generic Payload? Or maybe Font Size?

    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
         // Default generic properties handling (Label, Topic etc) acts automatically via PropertiesSheetManager
         // Specific: maybe "payload" to set initial text?
    }

    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        // Text usually just displays MQTT payload
    }

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {
        val container = view as? FrameLayout ?: return
        val tv = container.getChildAt(0) as? TextView ?: return
        tv.text = payload
    }
}
