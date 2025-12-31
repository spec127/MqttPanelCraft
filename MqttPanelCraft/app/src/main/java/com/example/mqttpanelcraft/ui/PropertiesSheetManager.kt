package com.example.mqttpanelcraft.ui

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.mqttpanelcraft.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.textfield.TextInputEditText

class PropertiesSheetManager(
    private val rootView: View,
    private val propertyContainer: View,
    private val onPropertyUpdated: (viewId: Int, name: String, w: Int, h: Int, color: String, topicConfig: String) -> Unit
) {

    private var selectedViewId: Int = View.NO_ID
    
    // UI Elements
    private val etPropName: EditText = rootView.findViewById(R.id.etPropName)
    private val etPropWidth: EditText = rootView.findViewById(R.id.etPropWidth)
    private val etPropHeight: EditText = rootView.findViewById(R.id.etPropHeight)
    private val etPropColor: EditText = rootView.findViewById(R.id.etPropColor)
    private val etPropTopicConfig: TextInputEditText = rootView.findViewById(R.id.etPropTopicConfig)
    private val btnSaveProps: Button = rootView.findViewById(R.id.btnSaveProps)
    
    init {
        setupListeners()
        etPropColor.isFocusable = false
        etPropColor.setOnClickListener { showColorPicker() }
    }

    private fun setupListeners() {
        btnSaveProps.setOnClickListener {
            if (selectedViewId != View.NO_ID) {
                try {
                    val w = etPropWidth.text.toString().toIntOrNull() ?: 100
                    val h = etPropHeight.text.toString().toIntOrNull() ?: 100
                    val name = etPropName.text.toString()
                    val color = etPropColor.text.toString()
                    val topicConfig = etPropTopicConfig.text.toString()
                    
                    onPropertyUpdated(selectedViewId, name, w, h, color, topicConfig)
                    Toast.makeText(rootView.context, "Properties Updated", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(rootView.context, "Invalid Input", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun showProperties(view: View, label: String, topicConfig: String) {
        selectedViewId = view.id
        etPropName.setText(label)
        etPropWidth.setText(view.width.toString())
        etPropHeight.setText(view.height.toString())
        etPropTopicConfig.setText(topicConfig)
        
        // Expand Bottom Sheet
        try {
            val behavior = BottomSheetBehavior.from(propertyContainer)
            if (behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        } catch (e: Exception) {
             e.printStackTrace()
             // Fallback if not a behavior view
             propertyContainer.visibility = View.VISIBLE
        }
    }

    private fun showColorPicker() {
        // Simple Color Picker Logic (Ported from Activity)
        val context = rootView.context
        val width = 600
        val height = 400
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val gradient = LinearGradient(0f, 0f, width.toFloat(), 0f,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED),
            null, Shader.TileMode.CLAMP)
        val paint = android.graphics.Paint()
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val darkGradient = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            null, Shader.TileMode.CLAMP)
        val darkPaint = android.graphics.Paint()
        darkPaint.shader = darkGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), darkPaint)

        val imageView = ImageView(context)
        imageView.setImageBitmap(bitmap)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Select Color")
            .setView(imageView)
            .setPositiveButton("Close", null)
            .create()

        imageView.setOnTouchListener { _, event ->
             if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                 val x = event.x.toInt().coerceIn(0, width - 1)
                 val y = event.y.toInt().coerceIn(0, height - 1)
                 val pixel = bitmap.getPixel(x, y)
                 val hex = String.format("#%06X", (0xFFFFFF and pixel))
                 etPropColor.setText(hex)
                 dialog.setTitle(hex)
             }
             true
        }
        dialog.show()
    }
}
