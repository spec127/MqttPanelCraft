package com.example.mqttpanelcraft.ui

import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar

/**
 * 獨立的調色盤對話框
 * 可在任何地方呼叫使用，提供完整的顏色選擇功能
 * 
 * @param context Context
 * @param initialColor 初始顏色（支援 #RRGGBB 或 #AARRGGBB 格式）
 * @param showAlpha 是否顯示 Alpha 透明度滑桿（預設 true）
 * @param onColorSelected 顏色選擇完成的回呼函式
 */
class ColorPickerDialog(
    private val context: Context,
    private val initialColor: String = "#FFFFFFFF",
    private val showAlpha: Boolean = true,
    private val onColorSelected: (String) -> Unit
) {
    
    private var popupWindow: PopupWindow? = null
    private val density = context.resources.displayMetrics.density
    
    /**
     * 顯示調色盤 Popup
     * @param anchorView 錨點 View（Popup 會顯示在此 View 上方）
     * @param gravity 顯示位置（預設為上方）
     */
    fun show(anchorView: View, gravity: Int = Gravity.TOP) {
        val popupRoot = buildColorPickerUI()
        
        popupWindow = PopupWindow(
            popupRoot,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 20f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        
        // 測量並定位
        popupRoot.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        
        val popupHeight = popupRoot.measuredHeight
        val anchorHeight = anchorView.height
        val yOffset = -popupHeight - anchorHeight - (10 * density).toInt()
        
        popupWindow?.showAsDropDown(anchorView, 0, yOffset)
    }
    
    /**
     * 關閉調色盤
     */
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }
    
    /**
     * 建立調色盤 UI
     */
    private fun buildColorPickerUI(): View {
        // Helper: 建立邊框 Drawable
        fun createBorderDrawable(): GradientDrawable {
            return GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((1 * density).toInt(), Color.LTGRAY)
                cornerRadius = 4 * density
            }
        }
        
        // Root Container
        val popupRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
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
        
        // Draw Spectrum (Rainbow gradient)
        val huePaint = android.graphics.Paint()
        huePaint.shader = LinearGradient(
            0f, 0f, spectrumW.toFloat(), 0f,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, spectrumW.toFloat(), spectrumH.toFloat(), huePaint)
        
        // Draw Saturation/Value Overlay
        val satValPaint = android.graphics.Paint()
        satValPaint.shader = LinearGradient(
            0f, 0f, 0f, spectrumH.toFloat(),
            intArrayOf(Color.WHITE, Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, spectrumW.toFloat(), spectrumH.toFloat(), satValPaint)
        
        val spectrumContainer = FrameLayout(context).apply {
            background = createBorderDrawable()
            setPadding((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
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
            layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), spectrumH + (4 * density).toInt())
            background = createBorderDrawable()
            visibility = if (showAlpha) View.VISIBLE else View.GONE
        }
        
        val alphaSeekBar = SeekBar(context).apply {
            max = 255
            progress = 255
            rotation = 270f
            layoutParams = FrameLayout.LayoutParams(spectrumH, (40 * density).toInt()).apply {
                gravity = Gravity.CENTER
            }
        }
        alphaContainer.addView(alphaSeekBar)
        
        topContainer.addView(spectrumContainer)
        topContainer.addView(alphaContainer)
        popupRoot.addView(topContainer)
        
        // Bottom: Hex Input
        val etHex = EditText(context).apply {
            hint = "#RRGGBB"
            textSize = 14f
            background = createBorderDrawable()
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (16 * density).toInt()
            }
            setText(initialColor)
            setSingleLine(true)
        }
        popupRoot.addView(etHex)
        
        // Logic: Color state management
        var currentRgb = Color.WHITE
        var currentAlpha = 255
        
        // Parse initial color
        try {
            val c = Color.parseColor(initialColor)
            currentAlpha = Color.alpha(c)
            currentRgb = c
            alphaSeekBar.progress = currentAlpha
        } catch (e: Exception) {}
        
        // Helper: Emit color update
        fun emitColor(fromInput: Boolean = false) {
            val finalColor = (currentAlpha shl 24) or (currentRgb and 0x00FFFFFF)
            val hex = String.format("#%08X", finalColor)
            
            if (!fromInput && etHex.text.toString() != hex) {
                etHex.tag = "programmatic"
                etHex.setText(hex)
                etHex.tag = null
            }
            
            onColorSelected(hex)
        }
        
        // Spectrum Touch Listener
        imgSpectrum.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val x = event.x.toInt().coerceIn(0, spectrumW - 1)
                val y = event.y.toInt().coerceIn(0, spectrumH - 1)
                val pixel = bitmap.getPixel(x, y)
                currentRgb = pixel
                emitColor()
            }
            true
        }
        
        // Alpha SeekBar Listener
        alphaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentAlpha = progress
                    emitColor()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Hex Input Listener
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
                            alphaSeekBar.progress = currentAlpha
                            onColorSelected(hex)
                        } catch (e: Exception) {}
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        return popupRoot
    }
}
