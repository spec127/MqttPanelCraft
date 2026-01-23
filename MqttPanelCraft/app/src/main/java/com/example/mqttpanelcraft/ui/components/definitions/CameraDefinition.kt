package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object CameraDefinition : IComponentDefinition {
    
    override val type = "CAMERA"
    override val defaultSize = Size(120, 100) // Default H was 100
    override val labelPrefix = "cam"

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        val btn = Button(context).apply { 
            text = "CAM"
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }
        container.addView(btn, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val btn = container.getChildAt(0) as? Button ?: return
        
        data.props["color"]?.let { colorCode ->
             try {
                 val color = android.graphics.Color.parseColor(colorCode)
                 btn.setBackgroundColor(color)
             } catch(_: Exception) {}
        }
    }

    override val propertiesLayoutId = 0 

    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {}

    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
         // Open Camera Stream?
    }

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {}
}
