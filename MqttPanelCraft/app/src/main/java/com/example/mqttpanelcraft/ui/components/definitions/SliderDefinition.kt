package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.google.android.material.slider.Slider

object SliderDefinition : IComponentDefinition {
    
    override val type = "SLIDER"
    override val defaultSize = Size(160, 100)
    override val labelPrefix = "slider"

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        val slider = Slider(context).apply {
            valueFrom = 0f
            valueTo = 100f
            value = 50f
            stepSize = 1.0f
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }
        container.addView(slider, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val slider = findSliderIn(container) ?: return
        
        data.props["color"]?.let { colorCode ->
             try {
                 val color = android.graphics.Color.parseColor(colorCode)
                 val colorStateList = android.content.res.ColorStateList.valueOf(color)
                 slider.thumbTintList = colorStateList
                 slider.trackActiveTintList = colorStateList
                 // slider.haloTintList = colorStateList.withAlpha(50) // Optional
             } catch(_: Exception) {}
        }
    }

    override val propertiesLayoutId = 0 // Could add Min/Max props

    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
    }

    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        val container = view as? FrameLayout ?: return
        val slider = findSliderIn(container) ?: return
        
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                // Throttle? Or send on stop?
                // Usually send on stop is safer for MQTT
            }
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                val topic = data.topicConfig
                if (topic.isNotEmpty()) {
                    sendMqtt(topic, slider.value.toInt().toString())
                }
            }
        })
    }

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {
        val container = view as? FrameLayout ?: return
        val slider = findSliderIn(container) ?: return
        try {
            val v = payload.toFloat()
            slider.value = v.coerceIn(slider.valueFrom, slider.valueTo)
        } catch(_: Exception) {}
    }

    private fun findSliderIn(container: FrameLayout): Slider? {
         for (i in 0 until container.childCount) {
             val child = container.getChildAt(i)
             if (child is Slider) return child
         }
         return null
    }
}
