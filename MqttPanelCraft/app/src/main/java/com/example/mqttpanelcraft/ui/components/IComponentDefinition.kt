package com.example.mqttpanelcraft.ui.components

import android.content.Context
import android.util.Size
import android.view.View
import com.example.mqttpanelcraft.model.ComponentData

/**
 * The "Soul" of a component.
 * Encapsulates its Identity (Type, Size), Appearance (Factory), 
 * Properties Logic (Binder), and Runtime Behavior (Behavior).
 */
interface IComponentDefinition {
    // 1. Identity & Defaults
    val type: String
    val defaultSize: Size // Ensure unify 100x100 if needed
    val labelPrefix: String // e.g. "button"

    // 2. Appearance (Factory)
    fun createView(context: Context, isEditMode: Boolean): View

    // 2.5 Dynamic Updates (Appearance changes after creation, e.g. Color)
    fun onUpdateView(view: View, data: ComponentData)

    // 3. Properties (Binder)
    val propertiesLayoutId: Int // Resource ID for specific properties (0 if none)
    fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit)

    // 4. Runtime Behavior (BehaviorManager)
    fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit)
    fun onMqttMessage(view: View, data: ComponentData, payload: String)
}
