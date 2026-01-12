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
import com.example.mqttpanelcraft.ui.properties.ButtonPropertyBinder
import com.example.mqttpanelcraft.ui.properties.IComponentPropertyBinder
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
    
    // Registry
    private val binders = mapOf<String, IComponentPropertyBinder>(
        "BUTTON" to ButtonPropertyBinder()
    )
    
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
        
        vPropColorPreview?.setOnClickListener { showColorPickerPopup() }
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
                val color = vPropColorPreview?.text.toString() ?: "#FFFFFF"
                val topicConfig = getFullTopic()
                
                // Construct updated data
                val updated = currentData!!.copy(
                    label = name,
                    width = wPx,
                    height = hPx,
                    topicConfig = topicConfig
                )
                if (color.isNotEmpty()) updated.props["color"] = color
                
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
        isBinding = true
        
        try {
            // ... (Existing binding)
            
            // Specific Props Architecture
            containerSpecificProps?.removeAllViews()
            binders[data.type]?.let { binder ->
                 val inflater = LayoutInflater.from(propertyContainer.context)
                 val root = inflater.inflate(binder.getLayoutId(), containerSpecificProps, true)
                 
                 binder.bind(root, data) { key, value ->
                     // Specific Property Updated
                     currentData?.props?.put(key, value)
                     saveCurrentProps() // Trigger full save
                 }
            }
            
            // ...
            etPropName?.setText(data.label)
            
            // Dimensions
            val density = propertyContainer.resources.displayMetrics.density
            val wDp = kotlin.math.round(view.width / density).toInt()
            val hDp = kotlin.math.round(view.height / density).toInt()
            etPropWidth?.setText(wDp.toString())
            etPropHeight?.setText(hDp.toString())
            
            // Color Logic (Try resolve background color)
            var colorHex = "#FFFFFF"
            // Prefer prop if set
            if (data.props.containsKey("color")) {
                colorHex = data.props["color"] ?: "#FFFFFF"
            } else if (view.background is android.graphics.drawable.ColorDrawable) {
                val colorInt = (view.background as android.graphics.drawable.ColorDrawable).color
                colorHex = String.format("#%08X", colorInt) // Use ARGB
            }

            // etPropColor?.setText(colorHex)
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
            val color = Color.parseColor(hex)
            // Update View Background (keeping stroke)
            val bg = vPropColorPreview?.background as? GradientDrawable ?: GradientDrawable()
            bg.setColor(color)
            val density = propertyContainer.resources.displayMetrics.density
            bg.setStroke((2 * density).toInt(), Color.parseColor("#808080"))
            bg.cornerRadius = (4 * density)
            vPropColorPreview?.background = bg
            
            // Update Text
            vPropColorPreview?.text = hex
            
            // Contrast Logic
            val contrastColor = if ((Color.red(color)*0.299 + Color.green(color)*0.587 + Color.blue(color)*0.114) > 186) Color.BLACK else Color.WHITE
            vPropColorPreview?.setTextColor(contrastColor)
            
            // tvColorHex?.text = hex.uppercase()
        } catch(e: Exception) {}
    }

    fun hide() {
        propertyContainer.visibility = View.GONE
        // Helper to find BS and hide it
        findBottomSheetBehavior()?.state = BottomSheetBehavior.STATE_HIDDEN
        currentData = null
    }
    
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

    // === Color Picker (Popup) ===
    private fun showColorPickerPopup() {
        val context = propertyContainer.context
        val density = context.resources.displayMetrics.density
        
        // Helper for Border
        fun createBorderDrawable(): android.graphics.drawable.Drawable {
            val gd = GradientDrawable()
            gd.setColor(Color.WHITE)
            gd.setStroke((1 * density).toInt(), Color.LTGRAY)
            gd.cornerRadius = 4 * density
            return gd
        }
        
        // Root: Vertical
        val popupRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            setBackgroundColor(Color.WHITE)
            background = GradientDrawable().apply {
                 setColor(Color.WHITE)
                 cornerRadius = 8 * density
                 setStroke((1 * density).toInt(), Color.GRAY)
            }
            elevation = 20f
        }
        
        // Top: Spectrum + Alpha
        val topContainer = LinearLayout(context).apply {
             orientation = LinearLayout.HORIZONTAL
             clipChildren = false
        }
        
        // 1. Spectrum Box
        val spectrumW = (150 * density).toInt()
        val spectrumH = (150 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(spectrumW, spectrumH, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Draw Spectrum
        val huePaint = android.graphics.Paint()
        huePaint.shader = LinearGradient(0f, 0f, spectrumW.toFloat(), 0f,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, spectrumW.toFloat(), spectrumH.toFloat(), huePaint)
        
        // Draw Sat/Val Overlay
        val satValPaint = android.graphics.Paint()
        satValPaint.shader = LinearGradient(0f, 0f, 0f, spectrumH.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, spectrumW.toFloat(), spectrumH.toFloat(), satValPaint)
        
        val spectrumContainer = FrameLayout(context).apply {
            background = createBorderDrawable()
            setPadding((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = (8 * density).toInt()
            }
        }
        
        val imgSpectrum = ImageView(context).apply {
            setImageBitmap(bitmap)
            layoutParams = FrameLayout.LayoutParams(spectrumW, spectrumH)
        }
        spectrumContainer.addView(imgSpectrum)
        
        // 2. Alpha Slider
        val alphaContainer = FrameLayout(context).apply {
             layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), spectrumH + (4 * density).toInt()) // Match height roughly
             background = createBorderDrawable()
        }
        val vSeek = SeekBar(context).apply {
            max = 255
            progress = 255
            rotation = 270f
            // Adjust translation to center rotated seekbar
            // Height of seekbar becomes Width.
            // visual width is spectrumH
            // Layout params width is 40dp.
            layoutParams = FrameLayout.LayoutParams(spectrumH, (40 * density).toInt()).apply {
                gravity = android.view.Gravity.CENTER
            }
        }
        alphaContainer.addView(vSeek)
        
        topContainer.addView(spectrumContainer)
        topContainer.addView(alphaContainer)
        popupRoot.addView(topContainer)

        // Bottom: Hex Input
        val etHex = EditText(context).apply {
            hint = "#RRGGBB"
            textSize = 14f
            background = createBorderDrawable()
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (16 * density).toInt()
            }
            // Set current color text IMMEDIATELY
            setText(vPropColorPreview?.text ?: "#FFFFFF")
            setSingleLine(true)
        }
        popupRoot.addView(etHex)

        // Window
        val popupWindow = PopupWindow(
            popupRoot, 
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 20f
        popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        
        // Logic
        var currentRgb = Color.WHITE
        var currentAlpha = 255
        
        fun emit(fromInput: Boolean = false) {
            val finalColor = (currentAlpha shl 24) or (currentRgb and 0x00FFFFFF)
            val hex = String.format("#%08X", finalColor)
            
            if (!fromInput) {
                // Determine if we need to update text
                // Only if it's different to avoid cursor jumping
                if (etHex.text.toString() != hex) {
                     // Temporarily remove listener? Or just set flag?
                     // actually setText triggers listener. 
                     // We need to differentiate.
                     etHex.tag = "programmatic"
                     etHex.setText(hex)
                     etHex.tag = null
                }
            }
            
            updateColorPreview(hex)
            saveCurrentProps()
        }

        // Spectrum Touch
        imgSpectrum.setOnTouchListener { _, event ->
             if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                 val x = event.x.toInt().coerceIn(0, spectrumW - 1)
                 val y = event.y.toInt().coerceIn(0, spectrumH - 1)
                 val pixel = bitmap.getPixel(x, y)
                 currentRgb = pixel
                 emit()
             }
             true
        }
        
        // Alpha Change
        vSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentAlpha = progress
                    emit()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Hex Input Change
        etHex.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (etHex.tag == "programmatic") return

                if (etHex.hasFocus()) {
                    val hex = s.toString()
                    if (hex.length >= 7) { 
                        try {
                            val c = Color.parseColor(hex)
                            currentAlpha = Color.alpha(c)
                            currentRgb = c 
                            
                            // Visuals only prevent loop
                            vSeek.progress = currentAlpha
                            updateColorPreview(hex)
                            saveCurrentProps()
                        } catch(e: Exception) {}
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        // Initial State Sync
        try {
            val currHex = vPropColorPreview?.text.toString()
            if (currHex.isNotEmpty()) {
                val c = Color.parseColor(currHex)
                currentAlpha = Color.alpha(c)
                currentRgb = c
                vSeek.progress = currentAlpha
            }
        } catch(e: Exception) {}

        // Measure and Position ABOVE
        popupRoot.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), 
                          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val h = popupRoot.measuredHeight
        
        // Anchor height
        val anchorH = vPropColorPreview?.height ?: 0
        
        // Y offset: -popupHeight - anchorHeight - margin
        val yOff = -h - anchorH - (10 * density).toInt()
        
        popupWindow.showAsDropDown(vPropColorPreview, 0, yOff)
    }
}
