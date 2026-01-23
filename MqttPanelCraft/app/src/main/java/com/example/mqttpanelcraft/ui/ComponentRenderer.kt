package com.example.mqttpanelcraft.ui

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.InterceptableFrameLayout

/**
 * Visual Renderer.
 * Responsible for syncing the ViewModel state (List<ComponentData>) to the Android View System.
 * It strictly follows One-Way Data Flow: Data -> View.
 */
class ComponentRenderer(
    private val canvasCanvas: FrameLayout,
    private val context: android.content.Context
) {

    // Keep track of existing views to avoid recreating them (Diffing)
    private val viewCache = mutableMapOf<Int, View>()

    private var selectedComponentId: Int? = null

    fun render(components: List<ComponentData>, isEditMode: Boolean, selectedId: Int? = null) {
        this.selectedComponentId = selectedId
        val currentIds = components.map { it.id }.toSet()

        // 1. Remove stale views (components that were deleted)
        val iterator = viewCache.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!currentIds.contains(entry.key)) {
                canvasCanvas.removeView(entry.value)
                // Also remove label
                val label = canvasCanvas.findViewWithTag<TextView>("LABEL_FOR_${entry.key}")
                if (label != null) canvasCanvas.removeView(label)
                iterator.remove()
            }
        }

        // 2. Add or Update views
        components.forEach { data ->
            val existingView = viewCache[data.id]
            val isSelected = (data.id == selectedId)

            if (existingView == null) {
                // CREATE NEW
                val def = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(data.type)
                val newView = def?.createView(context, isEditMode) ?: android.widget.TextView(context).apply { text = "Unknown: ${data.type}" }

                newView.id = data.id // Important: View ID matches Data ID
                
                // Set Layout
                val params = FrameLayout.LayoutParams(data.width, data.height)
                newView.layoutParams = params
                newView.x = data.x
                newView.y = data.y
                
                // Add to Canvas
                canvasCanvas.addView(newView)
                viewCache[data.id] = newView

                // Create Label
                val label = TextView(context).apply {
                    text = data.label
                    tag = "LABEL_FOR_${data.id}"
                    x = data.x
                    y = data.y + data.height + 4
                    visibility = View.VISIBLE
                }
                canvasCanvas.addView(label)
                
                // Apply Initial State
                updateViewState(newView, data, isEditMode, isSelected)

            } else {
                // UPDATE EXISTING
                
                // Position & Size Update
                if (existingView.x != data.x || existingView.y != data.y) {
                    existingView.x = data.x
                    existingView.y = data.y
                }
                
                val params = existingView.layoutParams
                if (params.width != data.width || params.height != data.height) {
                    params.width = data.width
                    params.height = data.height
                    existingView.layoutParams = params
                }

                // Apply State (Border, Handle, etc)
                updateViewState(existingView, data, isEditMode, isSelected)

                // Label Update
                val label = canvasCanvas.findViewWithTag<TextView>("LABEL_FOR_${data.id}")
                if (label != null) {
                    if (label.text != data.label) label.text = data.label
                    label.x = data.x
                    label.y = data.y + data.height + 4
                }
            }
        }
    }

    private fun updateViewState(view: View, data: ComponentData, isEditMode: Boolean, isSelected: Boolean) {
        if (view is InterceptableFrameLayout) {
             view.isEditMode = isEditMode
             
             // Border Logic
             if (isEditMode && isSelected) {
                 view.setBackgroundResource(R.drawable.component_border_selected)
             } else {
                 view.setBackgroundResource(R.drawable.component_border_normal)
             }

             // Handle Visibility
             val handle = view.findViewWithTag<View>("RESIZE_HANDLE")
             handle?.visibility = if (isEditMode && isSelected) View.VISIBLE else View.GONE
             // Update Handle Style (in case Factory didn't set it to the new drawable yet, or we enforce it)
             handle?.setBackgroundResource(R.drawable.bg_resize_handle)

             val clear = view.findViewWithTag<View>("CLEAR_BTN")
             clear?.visibility = if (isEditMode && isSelected) View.VISIBLE else View.GONE
             
             // Dynamic Update via Definition
             val def = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(data.type)
             def?.onUpdateView(view, data)
        }
    }
    
    // Helper to get view for behavior binding
    fun getView(id: Int): View? = viewCache[id]
}
