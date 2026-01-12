package com.example.mqttpanelcraft.ui

import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R

class CanvasInteractionManager(
    private val canvasCanvas: FrameLayout,
    private val guideOverlay: AlignmentOverlayView,
    private val bottomSheetPeekHeight: Int,
    private val callbacks: InteractionCallbacks
) {

    interface InteractionCallbacks {
        fun onComponentClicked(id: Int)
        fun onComponentMoved(id: Int, newX: Float, newY: Float)
        fun onComponentResized(id: Int, newW: Int, newH: Int)
        fun onComponentResizing(id: Int, newW: Int, newH: Int)
        fun onComponentDeleted(id: Int)
        fun onNewComponent(type: String, x: Float, y: Float)
        fun onDeleteZoneHover(isHovered: Boolean)
    }

    private val density = canvasCanvas.context.resources.displayMetrics.density
    private var bottomInset: Int = 0

    fun updateBottomInset(inset: Int) {
        this.bottomInset = inset
    }
    private var isDeleteHovered = false

    companion object {
        const val GRID_UNIT_DP = 10
        const val RESIZE_STEP_DP = 10
        const val DRAG_THRESHOLD = 10f
    }
    private val gridUnitPx = (GRID_UNIT_DP * density).toInt().coerceAtLeast(10)
    private val resizeStepPx = (RESIZE_STEP_DP * density).toInt().coerceAtLeast(10)

    private enum class Mode { IDLE, DRAGGING, RESIZING }
    private var currentMode = Mode.IDLE
    private var activeView: View? = null
    private var downX = 0f
    private var downY = 0f
    private var initX = 0f
    private var initY = 0f
    private var initW = 0
    private var initH = 0
    private var isDragDetected = false

    private var isGridSnapEnabled: () -> Boolean = { true }

    fun setup(isEditMode: () -> Boolean, isGridEnabled: () -> Boolean) {
        this.isGridSnapEnabled = isGridEnabled
        
        canvasCanvas.setOnTouchListener { _, event ->
            if (!isEditMode()) return@setOnTouchListener false
            handleTouch(event)
        }

        canvasCanvas.setOnDragListener { _, event ->
            if (!isEditMode()) return@setOnDragListener false
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                    // Start in IDLE state, will update in LOCATION
                    isDeleteHovered = false 
                }
                android.view.DragEvent.ACTION_DRAG_LOCATION -> {
                    val y = event.y
                    val effectiveBottomPadding = canvasCanvas.paddingBottom + bottomInset
                    // Drop Threshold: Canvas Height - Inset. 
                    // (Matches "dragging bottom of component" logic if component height ~ offset)
                    // Simple logic: If cursor is OVER the sheet (or bottom area), trigger delete.
                    val limitY = (canvasCanvas.height - effectiveBottomPadding).toFloat()
                    
                    val inZone = y > limitY
                    if (inZone != isDeleteHovered) {
                        isDeleteHovered = inZone
                        callbacks.onDeleteZoneHover(inZone)
                    }
                }
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    isDeleteHovered = false
                    callbacks.onDeleteZoneHover(false)
                }
                android.view.DragEvent.ACTION_DROP -> {
                    // Check if dropped in Delete Zone
                    val y = event.y
                    val effectiveBottomPadding = canvasCanvas.paddingBottom + bottomInset
                    val limitY = (canvasCanvas.height - effectiveBottomPadding).toFloat()
                    
                    if (y > limitY) {
                         // Dropped in Delete Zone - Cancel Creation
                         callbacks.onDeleteZoneHover(false)
                         // Do NOTHING (Deletion)
                    } else if (event.clipData != null && event.clipData.itemCount > 0) {
                        val type = event.clipData.getItemAt(0).text.toString()
                        callbacks.onNewComponent(type, event.x, event.y)
                    }
                }
            }

            true
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val rawX = event.rawX
        val rawY = event.rawY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = rawX
                downY = rawY
                isDragDetected = false
                
                val handle = findResizeHandleAt(x, y)
                if (handle != null) {
                    currentMode = Mode.RESIZING
                    activeView = handle 
                    initW = activeView!!.width
                    initH = activeView!!.height
                    canvasCanvas.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                val component = findComponentAt(x, y)
                if (component != null) {
                    currentMode = Mode.DRAGGING
                    activeView = component
                    initX = component.x
                    initY = component.y
                    canvasCanvas.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                callbacks.onComponentClicked(-1) 
                currentMode = Mode.IDLE
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = rawX - downX
                val dy = rawY - downY
                if (!isDragDetected && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                    isDragDetected = true
                }

                if (currentMode == Mode.DRAGGING && activeView != null) {
                    var newX = snap(initX + dx)
                    
                    // Y Axis Logic with Resistance
                    val rawTargetY = initY + dy
                    // Calculate snapped Y intent
                    val nominalY = snap(rawTargetY)
                    
                    val canvasH = canvasCanvas.height
                    val canvasW = canvasCanvas.width
                    val compH = activeView!!.height
                    val compW = activeView!!.width
                    
                    // Dynamic Safe Limit:
                    // Base padding + Dynamic Inset (Bottom Sheet) + Component Height + Buffer (30dp)
                    // User requested lowering by 60dp (+30) then another 20dp (+40)
                    // This allows dragging further into the sheet before resistance starts.
                    val effectiveBottomPadding = canvasCanvas.paddingBottom + bottomInset
                    val safeLimitY = (canvasH - effectiveBottomPadding - compH + (40 * density)).toFloat()
                    
                    var newY = nominalY
                    
                    if (nominalY > safeLimitY) {
                         // Resistance Zone
                         val excess = nominalY - safeLimitY
                         // Apply heavy damping (Resistance)
                         val resistedExcess = excess * 0.15f
                         newY = safeLimitY + resistedExcess
                         
                         // Threshold to trigger "Delete" state (e.g. pushed 15dp visually into the zone)
                         // AND Check X Axis (Center 25% only: 3/8 to 5/8)
                         val compCenter = newX + compW / 2f
                         val zoneMin = canvasW * 0.375f
                         val zoneMax = canvasW * 0.625f
                         
                         if (resistedExcess > 15 * density && compCenter >= zoneMin && compCenter <= zoneMax) { 
                             if (!isDeleteHovered) {
                                 isDeleteHovered = true
                                 callbacks.onDeleteZoneHover(true)
                                 activeView?.alpha = 0.5f
                             }
                         } else {
                             if (isDeleteHovered) {
                                 isDeleteHovered = false
                                 callbacks.onDeleteZoneHover(false)
                                 activeView?.alpha = 1.0f
                             }
                         }
                    } else {
                         // Normal Zone
                         if (isDeleteHovered) {
                             isDeleteHovered = false
                             callbacks.onDeleteZoneHover(false)
                             activeView?.alpha = 1.0f
                         }
                    }

                    // Clamp X
                    // Clamp X
                    if (newX < 0f) newX = 0f
                    if (newX > canvasW - compW) newX = (canvasW - compW).toFloat()
                    
                    // Clamp Y Top only (Bottom is allowed to overlap now)
                    if (newY < 0f) newY = 0f
                    
                    activeView!!.x = newX
                    activeView!!.y = newY
                    
                    // Update Label
                    findLabelFor(activeView!!)?.let {
                        it.x = newX
                        it.y = newY + activeView!!.height + 4
                    }

                    // Only align if NOT in delete zone
                    if (!isDeleteHovered) {
                        checkAlignment(activeView!!, compW, compH)
                    } else {
                        guideOverlay.clear()
                    }
                    return true
                }

                if (currentMode == Mode.RESIZING && activeView != null) {
                    val targetWDp = (initW + dx) / density
                    val targetHDp = (initH + dy) / density
                    
                    val stepDp = RESIZE_STEP_DP.toFloat() // 10f
                    
                    val newWDp = if (isGridSnapEnabled()) kotlin.math.round(targetWDp / stepDp) * stepDp else kotlin.math.round(targetWDp)
                    val newHDp = if (isGridSnapEnabled()) kotlin.math.round(targetHDp / stepDp) * stepDp else kotlin.math.round(targetHDp)
                    
                    var newW = kotlin.math.round(newWDp * density).toInt().coerceAtLeast(gridUnitPx)
                    var newH = kotlin.math.round(newHDp * density).toInt().coerceAtLeast(gridUnitPx)
                    
                    val params = activeView!!.layoutParams
                    params.width = newW
                    params.height = newH
                    activeView!!.layoutParams = params
                    activeView!!.requestLayout()
                    
                    val label = findLabelFor(activeView!!)
                    label?.let { it.y = activeView!!.y + newH + 4 }
                    
                    // Real-time alignment using calculated size, not current view size
                    checkAlignment(activeView!!, newW, newH)
                    
                    // Real-time UI update
                    callbacks.onComponentResizing(activeView!!.id, newW, newH)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                guideOverlay.clear()

                if (currentMode == Mode.DRAGGING && activeView != null) {
                    if (isDeleteHovered) {
                        callbacks.onComponentDeleted(activeView!!.id)
                        isDeleteHovered = false
                        callbacks.onDeleteZoneHover(false) 
                    } else if (isDragDetected) {
                        callbacks.onComponentMoved(activeView!!.id, activeView!!.x, activeView!!.y)
                    } else {
                        callbacks.onComponentClicked(activeView!!.id)
                    }
                    activeView?.alpha = 1.0f
                } else if (currentMode == Mode.RESIZING && activeView != null) {
                     if (isDragDetected) {
                         callbacks.onComponentResized(activeView!!.id, activeView!!.width, activeView!!.height)
                     }
                }

                currentMode = Mode.IDLE
                activeView = null
                isDeleteHovered = false
                return true
            }
        }
        return false
    }
    
    private fun checkAlignment(currentView: View, currentW: Int, currentH: Int) {
        if (isGridSnapEnabled()) {
             guideOverlay.clear()
             return
        }

        var newX = currentView.x
        var newY = currentView.y
        
        // Use PASSED width/height (essential for predictive resize alignment)
        val w = currentW
        val h = currentH
        
        val l = newX
        val t = newY
        val r = l + w
        val b = t + h
        val cx = l + w / 2f
        val cy = t + h / 2f
        
        val lines = mutableListOf<Float>() 
        val threshold = 10 * density 

        var snappedX = false
        var snappedY = false

        for (i in 0 until canvasCanvas.childCount) {
            val other = canvasCanvas.getChildAt(i)
            if (other == currentView || other.visibility != View.VISIBLE || other.tag?.toString()?.startsWith("LABEL") == true || other is AlignmentOverlayView) continue

            val ol = other.x
            val ot = other.y
            val ow = other.width
            val oh = other.height
            val or_ = ol + ow
            val ob = ot + oh
            val ocx = ol + ow / 2f
            val ocy = ot + oh / 2f

            // Vertical Alignment (X axis)
            if (!snappedX) {
                if (checkSnap(l, ol, threshold)) { newX = ol; snappedX = true; addVLine(lines, ol, t, b, ot, ob) }
                else if (checkSnap(l, or_, threshold)) { newX = or_; snappedX = true; addVLine(lines, or_, t, b, ot, ob) }
                else if (checkSnap(l, ocx, threshold)) { newX = ocx; snappedX = true; addVLine(lines, ocx, t, b, ot, ob) }
                
                else if (checkSnap(r, ol, threshold)) { newX = ol - w; snappedX = true; addVLine(lines, ol, t, b, ot, ob) }
                else if (checkSnap(r, or_, threshold)) { newX = or_ - w; snappedX = true; addVLine(lines, or_, t, b, ot, ob) }
                else if (checkSnap(r, ocx, threshold)) { newX = ocx - w; snappedX = true; addVLine(lines, ocx, t, b, ot, ob) }
                
                else if (checkSnap(cx, ol, threshold)) { newX = ol - w/2f; snappedX = true; addVLine(lines, ol, t, b, ot, ob) }
                else if (checkSnap(cx, or_, threshold)) { newX = or_ - w/2f; snappedX = true; addVLine(lines, or_, t, b, ot, ob) }
                else if (checkSnap(cx, ocx, threshold)) { newX = ocx - w/2f; snappedX = true; addVLine(lines, ocx, t, b, ot, ob) }
            }

            // Horizontal Alignment (Y axis)
            if (!snappedY) {
                if (checkSnap(t, ot, threshold)) { newY = ot; snappedY = true; addHLine(lines, ot, l, r, ol, or_) }
                else if (checkSnap(t, ob, threshold)) { newY = ob; snappedY = true; addHLine(lines, ob, l, r, ol, or_) }
                else if (checkSnap(t, ocy, threshold)) { newY = ocy; snappedY = true; addHLine(lines, ocy, l, r, ol, or_) }
                
                else if (checkSnap(b, ot, threshold)) { newY = ot - h; snappedY = true; addHLine(lines, ot, l, r, ol, or_) }
                else if (checkSnap(b, ob, threshold)) { newY = ob - h; snappedY = true; addHLine(lines, ob, l, r, ol, or_) }
                else if (checkSnap(b, ocy, threshold)) { newY = ocy - h; snappedY = true; addHLine(lines, ocy, l, r, ol, or_) }

                else if (checkSnap(cy, ot, threshold)) { newY = ot - h/2f; snappedY = true; addHLine(lines, ot, l, r, ol, or_) }
                else if (checkSnap(cy, ob, threshold)) { newY = ob - h/2f; snappedY = true; addHLine(lines, ob, l, r, ol, or_) }
                else if (checkSnap(cy, ocy, threshold)) { newY = ocy - h/2f; snappedY = true; addHLine(lines, ocy, l, r, ol, or_) }
            }
        }
        
        // If Resizing, we ONLY update params, we can't move X/Y via layout params in the same way? 
        // Wait, resizing usually only changes W/H. If we support Left/Top resize, X/Y changes too.
        // Current implementation is simple Bottom-Right resize (see MotionEvent).
        // Actually, current resize is usually only W/H increase?
        // Let's look at RESIZING logic again:
        // params.width = newW, params.height = newH.
        // X/Y are not modified during resize in current logic.
        // So aligning L, T, R, B...
        // If we only resize Bottom-Right, Top-Left (X/Y) is fixed.
        // We should only snap Width/Height then?
        // But if we snap Right edge to something, we adjust Width.
        // My `checkAlignment` updates `newX`/`newY`. This works for DRAGGING.
        // For RESIZING, we are NOT updating X/Y.
        
        // WARNING: Re-using `checkAlignment` for Resize is tricky if it modifies X/Y.
        // If simple interaction (Bottom-Right resize handle), we only care about R and B alignment.
        // AND we achieve it by changing Width/Height, not X/Y.
        
        // For now, I will disable alignment guides during RESIZE to avoid jumpiness, 
        // OR better: Only show lines, but don't snap X/Y.
        // But user asked for "Alignment line mismatch".
        // The issue is simply visual?
        // If I just draw lines based on `currentW/currentH`, it should be correct.
        
        // Let's modify: `checkAlignment` will Draw lines. It will also RETURN snapped bounds if needed?
        // Currently it modifies `currentView.x`. That's bad for Resize if we don't want to move Top-Left.
        
        if (currentMode == Mode.DRAGGING) {
             if (snappedX) currentView.x = newX
             if (snappedY) currentView.y = newY
             if (snappedX || snappedY) {
                 findLabelFor(currentView)?.let {
                     it.x = currentView.x
                     it.y = currentView.y + currentView.height + 4
                 }
             }
        }
        
        // Draw lines regardless
        guideOverlay.drawLines(lines.toFloatArray())
    }

    private fun checkSnap(val1: Float, val2: Float, threshold: Float): Boolean {
        return Math.abs(val1 - val2) < threshold
    }

    private fun addVLine(lines: MutableList<Float>, x: Float, y1: Float, y2: Float, oy1: Float, oy2: Float) {
        val min = Math.min(y1, oy1)
        val max = Math.max(y2, oy2)
        lines.add(x); lines.add(min); lines.add(x); lines.add(max)
    }
    private fun addHLine(lines: MutableList<Float>, y: Float, x1: Float, x2: Float, ox1: Float, ox2: Float) {
        val min = Math.min(x1, ox1)
        val max = Math.max(x2, ox2)
        lines.add(min); lines.add(y); lines.add(max); lines.add(y)
    }

    private fun snap(v: Float, step: Int = gridUnitPx): Float = if (isGridSnapEnabled()) (kotlin.math.round(v / step) * step).toFloat() else kotlin.math.round(v).toFloat()
    private fun snap(v: Int, step: Int = gridUnitPx): Float = snap(v.toFloat(), step)

    private fun findComponentAt(x: Float, y: Float): View? {
        for (i in canvasCanvas.childCount - 1 downTo 0) {
            val child = canvasCanvas.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child.id == R.id.backgroundGrid) continue 
            if (child.tag?.toString()?.startsWith("LABEL") == true) continue
            if (child is AlignmentOverlayView) continue
            if (x >= child.x && x <= child.x + child.width && y >= child.y && y <= child.y + child.height) return child
        }
        return null
    }

    private fun findResizeHandleAt(x: Float, y: Float): View? {
        for (i in canvasCanvas.childCount - 1 downTo 0) {
            val child = canvasCanvas.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child.id == R.id.backgroundGrid) continue
            if (child.tag?.toString()?.startsWith("LABEL") == true) continue
            val handle = child.findViewWithTag<View>("RESIZE_HANDLE")
            if (handle != null && handle.visibility == View.VISIBLE) {
                val hLeft = child.x + handle.x
                val hTop = child.y + handle.y
                if (x >= hLeft && x <= hLeft + handle.width && y >= hTop && y <= hTop + handle.height) {
                    return child
                }
            }
        }
        return null
    }

    private fun findLabelFor(view: View): View? = canvasCanvas.findViewWithTag("LABEL_FOR_${view.id}")
}
