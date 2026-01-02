package com.example.mqttpanelcraft.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class AlignmentOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#808080") // Darker Grey for better visibility
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private var showGrid = false
    private val density = context.resources.displayMetrics.density
    private val gridSize = 20 * density

    fun setGridVisible(visible: Boolean) {
        showGrid = visible
        invalidate()
    }
    
    fun isGridVisible(): Boolean = showGrid

    // Alignment Guide State
    private val paint = Paint().apply {
        color = Color.BLUE 
        style = Paint.Style.STROKE
        strokeWidth = 3f * density 
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    
    private val lines = mutableListOf<FloatArray>()

    fun clear() {
        lines.clear()
        invalidate()
    }

    fun addLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        lines.add(floatArrayOf(x1, y1, x2, y2))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (showGrid) {
             var x = 0f
             // Draw Grid Points instead of Lines (User Request #3)
             gridPaint.style = Paint.Style.FILL
             gridPaint.strokeWidth = 5f // Larger size for dot visibility
             
             while (x < width) {
                 var y = 0f
                 while (y < height) {
                     canvas.drawPoint(x, y, gridPaint)
                     y += gridSize
                 }
                 x += gridSize
             }
        }

        for (line in lines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], paint)
        }
    }
}
