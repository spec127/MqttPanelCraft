package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object ThermometerDefinition : IComponentDefinition {
    
    override val type = "THERMOMETER"
    override val defaultSize = Size(160, 100)
    override val labelPrefix = "temp"

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        val pb = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 50
            progressTintList = android.content.res.ColorStateList.valueOf(Color.RED)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                 gravity = Gravity.CENTER
            }
        }
        container.addView(pb, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val pb = findPbIn(container) ?: return
        
        data.props["color"]?.let { colorCode ->
             try {
                 val color = android.graphics.Color.parseColor(colorCode)
                 pb.progressTintList = android.content.res.ColorStateList.valueOf(color)
             } catch(_: Exception) {}
        }
    }

    override val propertiesLayoutId = 0 
    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {}

    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {}

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {
        val container = view as? FrameLayout ?: return
        val pb = findPbIn(container) ?: return
        try {
            pb.progress = payload.toFloat().toInt().coerceIn(0, 100)
        } catch(_: Exception) {}
    }

    private fun findPbIn(container: FrameLayout): ProgressBar? {
         for (i in 0 until container.childCount) {
             val child = container.getChildAt(i)
             if (child is ProgressBar) return child
         }
         return null
    }
}
