package com.example.mqttpanelcraft.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData

import com.google.android.material.bottomsheet.BottomSheetBehavior

class PropertiesSheetManager(
    private val propertyContainer: View, 
    private val onExpandRequest: () -> Unit, 
    private val onPropertyUpdated: (ComponentData) -> Unit,
    private val onClone: (Int) -> Unit,
    private val onResetTopic: (Int) -> Unit
) {

    private var isBinding = false
    private var selectedViewId: Int = View.NO_ID
    private var currentData: ComponentData? = null
    // Auto-restore logic
    private var lastComponentData: ComponentData? = null
    private var lastViewId: Int = View.NO_ID
    
    // Registry (Legacy binders removed)
    // private val binders = mapOf<String, IComponentPropertyBinder>(...)
    
    // UI Elements
    private val etPropName: EditText? = propertyContainer.findViewById(R.id.etPropName)
    private val etPropWidth: EditText? = propertyContainer.findViewById(R.id.etPropWidth)
    private val etPropHeight: EditText? = propertyContainer.findViewById(R.id.etPropHeight)
    
    // Color Picker UI
    private val vPropColorPreview: TextView? = propertyContainer.findViewById(R.id.vPropColorPreview)
    // private val etPropColor: EditText? = propertyContainer.findViewById(R.id.etPropColor) // Removed
    private val tvColorHex: TextView? = propertyContainer.findViewById(R.id.tvColorHex)
    
    // Composite Topic
    private val tvTopicPrefix: TextView? = propertyContainer.findViewById(R.id.tvTopicPrefix)
    private val etTopicName: EditText? = propertyContainer.findViewById(R.id.etTopicName)
    private val tvTopicSuffix: TextView? = propertyContainer.findViewById(R.id.tvTopicSuffix)
    private val btnTopicReset: View? = propertyContainer.findViewById(R.id.btnTopicReset)
    private val btnTopicCopy: View? = propertyContainer.findViewById(R.id.btnTopicCopy)
    
    private val btnSaveProps: Button? = propertyContainer.findViewById(R.id.btnSaveProps)
    
    // Specific Props
    private val containerSpecificProps: LinearLayout? = propertyContainer.findViewById(R.id.containerSpecificProps)
    
    // NEW: Button Specific Props
    private val containerButtonProps: View? = propertyContainer.findViewById(R.id.containerButtonProps)
    private val etPropPayload: EditText? = propertyContainer.findViewById(R.id.etPropPayload)
    private val etPropButtonText: EditText? = propertyContainer.findViewById(R.id.etPropButtonText)
    private val ivPropIconPreview: ImageView? = propertyContainer.findViewById(R.id.ivPropIconPreview)
    private val btnSelectIcon: View? = propertyContainer.findViewById(R.id.btnSelectIcon)
    private val btnClearIcon: View? = propertyContainer.findViewById(R.id.btnClearIcon)

    init {
        setupListeners()
    }

    private fun setupListeners() {
        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isBinding) saveCurrentProps()
            }
        }
        
        etPropName?.addTextChangedListener(textWatcher)
        etPropWidth?.addTextChangedListener(textWatcher)
        etPropHeight?.addTextChangedListener(textWatcher)
        // etPropColor is hidden, driven by Popup
        etTopicName?.addTextChangedListener(textWatcher)
        
        btnSaveProps?.visibility = View.GONE
        
        propertyContainer.findViewById<View>(R.id.btnClone)?.setOnClickListener {
            if (selectedViewId != View.NO_ID) onClone(selectedViewId)
        }
        
        btnTopicReset?.setOnClickListener {
            if (selectedViewId != View.NO_ID) onResetTopic(selectedViewId)
        }
        
        btnTopicCopy?.setOnClickListener {
             val full = getFullTopic()
             val clipboard = propertyContainer.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
             val clip = ClipData.newPlainText("Topic", full)
             clipboard.setPrimaryClip(clip)
             Toast.makeText(propertyContainer.context, "Topic Copied", Toast.LENGTH_SHORT).show()
        }
        
        vPropColorPreview?.setOnClickListener {
            val currentColor = vPropColorPreview?.text.toString()
            val initialColor = if (currentColor == "Default") "#FFFFFFFF" else currentColor
            
            ColorPickerDialog(
                context = propertyContainer.context,
                initialColor = initialColor,
                showAlpha = true
            ) { selectedHex ->
                updateColorPreview(selectedHex)
                saveCurrentProps()
            }.show(vPropColorPreview!!)
        }
        
        // Button Props Listeners
        etPropPayload?.addTextChangedListener(textWatcher)
        etPropButtonText?.addTextChangedListener(textWatcher)
        
        btnSelectIcon?.setOnClickListener {
            showIconSelector(btnSelectIcon)
        }
        
        btnClearIcon?.setOnClickListener {
            updateIconPreview(null)
            saveCurrentProps()
        }
    }
    
    private fun getFullTopic(): String {
         val prefix = tvTopicPrefix?.text.toString()
         val name = etTopicName?.text.toString()
         val suffix = tvTopicSuffix?.text.toString()
         return "$prefix$name$suffix"
    }

    fun updateDimensions(wPx: Int, hPx: Int) {
        if (isBinding) return
        isBinding = true
        val density = propertyContainer.resources.displayMetrics.density
        val wDp = kotlin.math.round(wPx / density).toInt()
        val hDp = kotlin.math.round(hPx / density).toInt()
        
        etPropWidth?.setText(wDp.toString())
        etPropHeight?.setText(hDp.toString())
        isBinding = false
        
        // Don't auto-save dimensions during drag, it might cause loops or jitter.
        // Wait, User wanted "Real-time dimension updates". 
        // If Model updates Activity, Activity calls this.
        // If this assumes "User Typed", it triggers save. But `isBinding` prevents loop.
        // But if user drags handle, Activity updates View.
        // Activity also updates VM.
        // So this is for synchronization only.
    }

    private fun saveCurrentProps() {
        if (selectedViewId != View.NO_ID && currentData != null) {
            try {
                val density = propertyContainer.resources.displayMetrics.density
                
                val wDp = etPropWidth?.text.toString().toFloatOrNull() ?: 100f
                val hDp = etPropHeight?.text.toString().toFloatOrNull() ?: 100f
                
                val wPx = (wDp * density).toInt()
                val hPx = (hDp * density).toInt()
                
                val name = etPropName?.text.toString()
                var color = vPropColorPreview?.text.toString()
                if (color == "Default") color = ""

                val topicConfig = getFullTopic()
                
                // Construct updated data
                val updated = currentData!!.copy(
                    label = name,
                    width = wPx,
                    height = hPx,
                    topicConfig = topicConfig
                )
                if (color.isNotEmpty() && color != "#FFFFFF") {
                     updated.props["color"] = color
                } else if (color == "#FFFFFF") {
                     // Explicit white is allowed if user selected it? 
                     // Or should we treat white as default?
                     // If User explicitly picked White, let's save it.
                     updated.props["color"] = color
                } else {
                     // Remove color prop if it was reset (Implement later if needed, for now just don't add)
                     updated.props.remove("color")
                }
                
                // Save Button Props
                if (currentData?.type == "BUTTON") {
                    val payload = etPropPayload?.text.toString()
                    val btnText = etPropButtonText?.text.toString()
                    val iconKey = ivPropIconPreview?.tag as? String ?: ""
                    
                    updated.props["payload"] = payload
                    updated.props["text"] = btnText
                    if (iconKey.isNotEmpty()) {
                        updated.props["icon"] = iconKey
                    } else {
                        updated.props.remove("icon")
                    }
                }
                
                // Specific properties are updated via callback in Binder, but they might need to be merged?
                // Actually binder updates `data.props` directly in my implementation of Binder?
                // ButtonPropertyBinder calls `onUpdate(key, value)`.
                // WE need to handle that callback.
                
                onPropertyUpdated(updated)
            } catch (e: Exception) {
            }
        }
    }
    
    fun clear() {
        isBinding = true
        selectedViewId = View.NO_ID
        currentData = null
        etPropName?.setText("")
        etPropWidth?.setText("")
        etPropHeight?.setText("")
        etPropHeight?.setText("")
        // etPropColor?.setText("")
        vPropColorPreview?.text = "#FFFFFF"
        // tvColorHex?.text = ""
        vPropColorPreview?.background = null
        etTopicName?.setText("")
        tvTopicPrefix?.text = ""
        tvTopicSuffix?.text = ""
        containerSpecificProps?.removeAllViews()
        isBinding = false
    }

    fun showProperties(view: View, data: ComponentData) {
        selectedViewId = view.id
        currentData = data
        lastViewId = view.id
        lastComponentData = data
        isBinding = true
        
        try {
            // ... (Existing binding)
            
            // Specific Props Architecture
            containerSpecificProps?.removeAllViews()
            
            // PRIORITY 1: Definition Architecture
            val def = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(data.type)
            if (def != null && def.propertiesLayoutId != 0) {
                 val inflater = LayoutInflater.from(propertyContainer.context)
                 val root = inflater.inflate(def.propertiesLayoutId, containerSpecificProps, true)
                 def.bindPropertiesPanel(root, data) { key: String, value: String ->
                      currentData?.props?.put(key, value)
                      saveCurrentProps()
                 }
            } 

            // Legacy Fallback removed as files are deleted.
            // Future components must implement IComponentDefinition.
            
            // ...
            etPropName?.setText(data.label)
            
            // Dimensions
            val density = propertyContainer.resources.displayMetrics.density
            // Fix: Use data dimensions instead of View dimensions which might be 0 before layout
            val wDp = kotlin.math.round(data.width / density).toInt()
            val hDp = kotlin.math.round(data.height / density).toInt()
            etPropWidth?.setText(wDp.toString())
            etPropHeight?.setText(hDp.toString())
            
            // Color Logic (Try resolve background color)
            var colorHex = "" // Default to empty (Transparent/Default)
            
            // Prefer prop if set
            if (data.props.containsKey("color")) {
                colorHex = data.props["color"] ?: ""
            }
            
            updateColorPreview(colorHex)
            
            // Button Specific Visibility & Binding
            if (data.type == "BUTTON") {
                containerButtonProps?.visibility = View.VISIBLE
                
                val payload = data.props["payload"] ?: "1"
                val btnText = data.props["text"] ?: ""
                val iconKey = data.props["icon"]
                
                etPropPayload?.setText(payload)
                etPropButtonText?.setText(btnText)
                updateIconPreview(iconKey)
                
            } else {
                containerButtonProps?.visibility = View.GONE
            }

            
            // If empty, we show "Default" in UI, but don't set text to #FFFFFF
            // updateColorPreview handles empty string now
            updateColorPreview(colorHex)
            
            // Topic Parsing
            val topicConfig = data.topicConfig
            val parts = topicConfig.split("/")
            
            // Standard Format: ProjectName/ProjectId/Rest...
            if (parts.size >= 2) {
                 val prefixStr = "${parts[0]}/${parts[1]}/"
                 val nameStr = if (parts.size > 2) parts.drop(2).joinToString("/") else ""
                 
                 tvTopicPrefix?.text = prefixStr
                 etTopicName?.setText(nameStr)
                 tvTopicSuffix?.text = "" // No suffix logic anymore
            } else {
                // Fallback for non-standard topics
                tvTopicPrefix?.text = ""
                etTopicName?.setText(topicConfig)
                tvTopicSuffix?.text = ""
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isBinding = false
        }
        
        propertyContainer.visibility = View.VISIBLE
        forceExpandBottomSheet()
        onExpandRequest()
    }
    
    private fun updateColorPreview(hex: String) {
        try {
            if (hex.isEmpty() || hex == "Default") {
                 vPropColorPreview?.text = "Default"
                 vPropColorPreview?.setTextColor(Color.BLACK)
                 vPropColorPreview?.setShadowLayer(0f, 0f, 0f, 0) // Clear shadow
                 
                 // Set preview to transparent grid or white with border
                 val bg = vPropColorPreview?.background as? GradientDrawable ?: GradientDrawable()
                 bg.setColor(Color.WHITE) 
                 val density = propertyContainer.resources.displayMetrics.density
                 bg.setStroke((1 * density).toInt(), Color.LTGRAY, (5 * density).toFloat(), (3 * density).toFloat()) // Dashed border
                 bg.cornerRadius = (4 * density)
                 vPropColorPreview?.background = bg
                 return
            }

            val color = Color.parseColor(hex)
            // Update View Background (keeping stroke)
            val bg = vPropColorPreview?.background as? GradientDrawable ?: GradientDrawable()
            bg.setColor(color)
            val density = propertyContainer.resources.displayMetrics.density
            bg.setStroke((2 * density).toInt(), Color.parseColor("#808080")) // Solid border
            bg.cornerRadius = (4 * density)
            vPropColorPreview?.background = bg
            
            // Update Text
            vPropColorPreview?.text = hex
            
            // Contrast Logic (Luminance + Alpha)
            // If Alpha < 180 (approx 70%), use Black text because background is transparent
            val alpha = Color.alpha(color)
            val luminescence = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color))
            
            val contrastColor = if (alpha < 180 || luminescence > 186) Color.BLACK else Color.WHITE
            
            vPropColorPreview?.setTextColor(contrastColor)
            vPropColorPreview?.setShadowLayer(0f, 0f, 0f, 0) // Ensure no shadow
            
        } catch(e: Exception) {}
    }

    fun hide() {
        propertyContainer.visibility = View.GONE
        // Helper to find BS and hide it
        findBottomSheetBehavior()?.state = BottomSheetBehavior.STATE_HIDDEN
        currentData = null
        // Note: We keep lastComponentData for restore logic
    }
    
    fun getLastSelectedId(): Int? = if (lastComponentData != null && lastViewId != View.NO_ID) lastViewId else null
    
    fun restoreLastState(): Boolean {
        // We prefer external restoration via ViewModel, so this might be redundant if used with Activity logic,
        // but keeping it for manual internal restore if needed.
        if (lastComponentData != null && lastViewId != View.NO_ID) {
            val view = propertyContainer.rootView.findViewById<View>(lastViewId)
            if (view != null) {
                showProperties(view, lastComponentData!!)
                return true
            }
        }
        return false
    }
    
    fun hasLastState(): Boolean = lastComponentData != null
    
    private fun forceExpandBottomSheet() {
        val behavior = findBottomSheetBehavior() ?: return
        behavior.isHideable = false 
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
    
    private fun findBottomSheetBehavior(): BottomSheetBehavior<View>? {
        var currentParent = propertyContainer.parent
        while (currentParent is View) {
            try {
                val params = currentParent.layoutParams
                if (params is androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
                     if (params.behavior is BottomSheetBehavior) {
                         return params.behavior as BottomSheetBehavior<View>
                     }
                }
            } catch(e: Exception) {}
            currentParent = currentParent.parent
        }
        return null
    }

    // === Color Picker 已移至 ColorPickerDialog.kt ===
    
    private fun updateIconPreview(iconKey: String?) {
        if (iconKey.isNullOrEmpty()) {
            ivPropIconPreview?.setImageDrawable(null)
            ivPropIconPreview?.tag = null
            btnClearIcon?.visibility = View.GONE
        } else {
            // Map keys to drawables (Simple map for now)
            val resId = getDrawableIdFromKey(iconKey)
            ivPropIconPreview?.setImageResource(resId)
            ivPropIconPreview?.tag = iconKey
            btnClearIcon?.visibility = View.VISIBLE
        }
    }
    
    private fun getDrawableIdFromKey(key: String): Int {
        return when(key) {
            "plus" -> android.R.drawable.ic_input_add
            "delete" -> android.R.drawable.ic_delete
            "send" -> android.R.drawable.ic_menu_send
            "edit" -> android.R.drawable.ic_menu_edit
            "info" -> android.R.drawable.ic_dialog_info
            "mic" -> android.R.drawable.btn_star // Placeholder for mic
            else -> android.R.drawable.ic_menu_help
        }
    }
    
    private fun showIconSelector(anchor: View) {
        val popup = android.widget.PopupMenu(propertyContainer.context, anchor)
        popup.menu.add("Plus").setOnMenuItemClickListener { updateIconPreview("plus"); saveCurrentProps(); true }
        popup.menu.add("Delete").setOnMenuItemClickListener { updateIconPreview("delete"); saveCurrentProps(); true }
        popup.menu.add("Send").setOnMenuItemClickListener { updateIconPreview("send"); saveCurrentProps(); true }
        popup.menu.add("Edit").setOnMenuItemClickListener { updateIconPreview("edit"); saveCurrentProps(); true }
        popup.menu.add("Info").setOnMenuItemClickListener { updateIconPreview("info"); saveCurrentProps(); true }
        popup.show()
    }
}
