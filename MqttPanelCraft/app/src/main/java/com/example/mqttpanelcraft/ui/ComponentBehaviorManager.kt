package com.example.mqttpanelcraft.ui

import android.content.Context
import android.view.View
import com.example.mqttpanelcraft.model.ComponentData


/**
 * Registry and dispatcher for Component Behaviors.
 * Strategy Pattern: Delegates logic to IComponentBehavior implementations.
 */
class ComponentBehaviorManager(
    private val context: Context,
    private val projectIdProvider: () -> String?,
    private val sendMqtt: (topic: String, payload: String) -> Unit
) {
    // Registry (Legacy behaviors removed)
    // private val behaviors = mapOf<String, IComponentBehavior>(...)

    fun attachBehavior(view: View, data: ComponentData) {
        val def = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(data.type)
        if (def != null) {
            def.attachBehavior(view, data, sendMqtt)
        } 
    }

    fun onMqttMessageReceived(view: View, data: ComponentData, payload: String) {
        val def = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(data.type)
        if (def != null) {
            def.onMqttMessage(view, data, payload)
        }
    }
}
