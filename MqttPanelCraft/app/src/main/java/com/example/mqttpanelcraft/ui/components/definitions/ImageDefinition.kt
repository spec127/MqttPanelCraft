package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object ImageDefinition : IComponentDefinition {
    
    override val type = "IMAGE"
    override val defaultSize = Size(100, 100)
    override val labelPrefix = "img"

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        val iv = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_gallery) // Placeholder
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        container.addView(iv, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val iv = container.getChildAt(0) as? ImageView ?: return
        
        data.props["color"]?.let { colorCode ->
             try {
                 val color = android.graphics.Color.parseColor(colorCode)
                 iv.setColorFilter(color) // Tint the image
             } catch(_: Exception) {}
        }
    }

    override val propertiesLayoutId = 0 

    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
    }

    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        // Image usually receives base64?
    }

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {
        val container = view as? FrameLayout ?: return
        val iv = container.getChildAt(0) as? ImageView ?: return
        
        // Setup Base64 decoding if needed. 
        // For now, assume payload might represent a URL or something.
    }
}
