package com.example.mqttpanelcraft.ui

import android.graphics.Point
import android.graphics.Rect
import android.view.DragEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.mqttpanelcraft.utils.CrashLogger

class CanvasManager(
    private val canvasCanvas: FrameLayout,
    private val guideOverlay: AlignmentOverlayView,
    private val dropDeleteZone: View,
    private val onComponentDropped: (View) -> Unit,
    private val onComponentMoved: (View) -> Unit,
    private val onComponentDeleted: (View) -> Unit,
    private val onCreateNewComponent: (String, Float, Float) -> Unit // New Callback
) {

    private val density = canvasCanvas.context.resources.displayMetrics.density

    private val cachedCanvasLoc = IntArray(2)
    private var lastCheckTime = 0L

    fun setupDragListener(isEditMode: () -> Boolean) {
        canvasCanvas.setOnDragListener { _, event ->
            if (!isEditMode()) return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DROP -> {
                    handleDrop(event)
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    canvasCanvas.getLocationOnScreen(cachedCanvasLoc)
                    dropDeleteZone.visibility = View.VISIBLE
                }
                DragEvent.ACTION_DRAG_LOCATION -> {
                    val now = System.currentTimeMillis()
                    if (now - lastCheckTime > 32) { // Cap at ~30 FPS for checks
                        lastCheckTime = now
                        val localState = event.localState as? View
                        
                        // For existing components (parent == canvas), usage is straightforward.
                        // For NEW components (parent != canvas), we need to estimate size/id?
                        // Actually, alignment guides for new components are tricky if we don't have a view yet.
                        // We will skip alignment check for new components for now, or assume default size.
                        
                        if (localState != null && localState.parent == canvasCanvas) {
                            checkAlignment(event.x, event.y, localState)
                            
                            val screenX = event.x + cachedCanvasLoc[0]
                            val screenY = event.y + cachedCanvasLoc[1]
                            
                            checkDeleteZoneHover(screenX, screenY, localState)
                        }
                    }
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    guideOverlay.clear()
                    dropDeleteZone.visibility = View.GONE
                    val localState = event.localState as? View
                    
                    if (!event.result) {
                         // Drop failed or cancelled
                         localState?.visibility = View.VISIBLE
                         findLabelView(localState)?.visibility = View.VISIBLE
                         localState?.alpha = 1.0f
                    }
                }
            }
            true
        }
    }

    private fun handleDrop(event: DragEvent) {
        val x = event.x
        val y = event.y
        val localView = event.localState as? View

        // Check Delete Zone Hit
        val locations = IntArray(2)
        dropDeleteZone.getLocationOnScreen(locations)
        val zoneRect = Rect(locations[0], locations[1], locations[0]+dropDeleteZone.width, locations[1]+dropDeleteZone.height)
        
        if (cachedCanvasLoc[0] == 0 && cachedCanvasLoc[1] == 0) {
            canvasCanvas.getLocationOnScreen(cachedCanvasLoc)
        }
        val screenX = event.x + cachedCanvasLoc[0]
        val screenY = event.y + cachedCanvasLoc[1]
        
        if (zoneRect.contains(screenX.toInt(), screenY.toInt())) {
            if (localView != null && localView.parent == canvasCanvas) {
                onComponentDeleted(localView)
                canvasCanvas.removeView(localView)
                findLabelView(localView)?.let { canvasCanvas.removeView(it) }
            }
            return
        }

        if (localView != null && localView.parent == canvasCanvas) {
            // Existing component moved
            guideOverlay.clear()
            
            var finalX = x - (localView.width / 2)
            var finalY = y - (localView.height / 2)

            val snapPos = calculateSnap(x, y, localView.width, localView.height, localView)
            if (snapPos != null) {
                finalX = snapPos.x.toFloat()
                finalY = snapPos.y.toFloat()
            }
            
            finalX = finalX.coerceIn(0f, (canvasCanvas.width - localView.width).toFloat())
            finalY = finalY.coerceIn(0f, (canvasCanvas.height - localView.height).toFloat())

            localView.x = finalX
            localView.y = finalY
            localView.visibility = View.VISIBLE
            localView.alpha = 1.0f

            findLabelView(localView)?.let { label ->
                label.x = finalX
                label.y = finalY + localView.height + 4
                label.visibility = View.VISIBLE
            }

            onComponentMoved(localView)

        } else {
            // New component from Sidebar
            val item = event.clipData.getItemAt(0)
            val tag = item.text.toString()
            
            // Invoke callback to create component
            // We pass center coordinates (event.x, event.y), calling logic should handle offset (width/2)
            onCreateNewComponent(tag, x, y)
        }
    }
    
    fun getSnappedPosition(rawX: Float, rawY: Float, w: Int, h: Int, viewToExclude: View?): Point {
        return calculateSnap(rawX, rawY, w, h, viewToExclude) ?: Point(rawX.toInt(), rawY.toInt())
    }

    private fun calculateSnap(centerRawX: Float, centerRawY: Float, w: Int, h: Int, currentView: View?): Point? {
        val threshold = 16f * density

        // Convert center drag coords to Top-Left for snapping logic
        val rawLeft = centerRawX - w / 2f
        val rawTop = centerRawY - h / 2f

        var snapX = rawLeft
        var snapY = rawTop
        var hasSnapX = false
        var hasSnapY = false

        val myLeft = rawLeft
        val myRight = rawLeft + w
        val myTop = rawTop
        val myBottom = rawTop + h
        val myCenterX = centerRawX
        val myCenterY = centerRawY
        
        var minDiffX = threshold
        var minDiffY = threshold

        for (i in 0 until canvasCanvas.childCount) {
            val target = canvasCanvas.getChildAt(i)
            if (target == currentView) continue
            val tag = target.tag as? String ?: continue
            if (tag.startsWith("LABEL_FOR_")) continue

            val tLeft = target.x
            val tRight = target.x + target.width
            val tTop = target.y
            val tBottom = target.y + target.height
            val tCenterX = target.x + target.width / 2f
            val tCenterY = target.y + target.height / 2f

            // X Loop
            if (kotlin.math.abs(myLeft - tLeft) < minDiffX) { snapX = tLeft; minDiffX = kotlin.math.abs(myLeft - tLeft); hasSnapX = true }
            if (kotlin.math.abs(myLeft - tRight) < minDiffX) { snapX = tRight; minDiffX = kotlin.math.abs(myLeft - tRight); hasSnapX = true }
            if (kotlin.math.abs(myRight - tLeft) < minDiffX) { snapX = tLeft - w; minDiffX = kotlin.math.abs(myRight - tLeft); hasSnapX = true }
             if (kotlin.math.abs(myRight - tRight) < minDiffX) { snapX = tRight - w; minDiffX = kotlin.math.abs(myRight - tRight); hasSnapX = true }
            if (kotlin.math.abs(myCenterX - tCenterX) < minDiffX) { snapX = tCenterX - w / 2f; minDiffX = kotlin.math.abs(myCenterX - tCenterX); hasSnapX = true }

            // Y Loop
            if (kotlin.math.abs(myTop - tTop) < minDiffY) { snapY = tTop; minDiffY = kotlin.math.abs(myTop - tTop); hasSnapY = true }
            if (kotlin.math.abs(myTop - tBottom) < minDiffY) { snapY = tBottom; minDiffY = kotlin.math.abs(myTop - tBottom); hasSnapY = true }
            if (kotlin.math.abs(myBottom - tTop) < minDiffY) { snapY = tTop - h; minDiffY = kotlin.math.abs(myBottom - tTop); hasSnapY = true }
            if (kotlin.math.abs(myBottom - tBottom) < minDiffY) { snapY = tBottom - h; minDiffY = kotlin.math.abs(myBottom - tBottom); hasSnapY = true }
            if (kotlin.math.abs(myCenterY - tCenterY) < minDiffY) { snapY = tCenterY - h / 2f; minDiffY = kotlin.math.abs(myCenterY - tCenterY); hasSnapY = true }
        }

        if (hasSnapX || hasSnapY) {
            return Point(if(hasSnapX) snapX.toInt() else rawLeft.toInt(), if(hasSnapY) snapY.toInt() else rawTop.toInt())
        }
        return null
    }

    private fun checkAlignment(centerX: Float, centerY: Float, currentView: View) {
        guideOverlay.clear()
        val threshold = 2f * density

        val w = currentView.width
        val h = currentView.height

        // Fix: Use DYNAMIC coordinates passed from Event, not static view properties
        val myLeft = centerX - w / 2f
        val myRight = centerX + w / 2f
        val myTop = centerY - h / 2f
        val myBottom = centerY + h / 2f
        val myCenterX = centerX
        val myCenterY = centerY

        for (i in 0 until canvasCanvas.childCount) {
             val target = canvasCanvas.getChildAt(i)
             if (target == currentView) continue
             val tag = target.tag as? String ?: continue
             if (tag.startsWith("LABEL_FOR_")) continue

             val tLeft = target.x
             val tRight = target.x + target.width
             val tTop = target.y
             val tBottom = target.y + target.height
             val tCenterX = target.x + target.width / 2f
             val tCenterY = target.y + target.height / 2f

             // X Matches
             if (kotlin.math.abs(myLeft - tLeft) < threshold) guideOverlay.addLine(tLeft, kotlin.math.min(myTop, tTop), tLeft, kotlin.math.max(myBottom, tBottom))
             if (kotlin.math.abs(myLeft - tRight) < threshold) guideOverlay.addLine(tRight, kotlin.math.min(myTop, tTop), tRight, kotlin.math.max(myBottom, tBottom))
             if (kotlin.math.abs(myRight - tLeft) < threshold) guideOverlay.addLine(tLeft, kotlin.math.min(myTop, tTop), tLeft, kotlin.math.max(myBottom, tBottom))
             if (kotlin.math.abs(myRight - tRight) < threshold) guideOverlay.addLine(tRight, kotlin.math.min(myTop, tTop), tRight, kotlin.math.max(myBottom, tBottom))
             if (kotlin.math.abs(myCenterX - tCenterX) < threshold) guideOverlay.addLine(tCenterX, kotlin.math.min(myTop, tTop), tCenterX, kotlin.math.max(myBottom, tBottom))

             // Y Matches
             if (kotlin.math.abs(myTop - tTop) < threshold) guideOverlay.addLine(kotlin.math.min(myLeft, tLeft), tTop, kotlin.math.max(myRight, tRight), tTop)
             if (kotlin.math.abs(myTop - tBottom) < threshold) guideOverlay.addLine(kotlin.math.min(myLeft, tLeft), tBottom, kotlin.math.max(myRight, tRight), tBottom)
             if (kotlin.math.abs(myBottom - tTop) < threshold) guideOverlay.addLine(kotlin.math.min(myLeft, tLeft), tTop, kotlin.math.max(myRight, tRight), tTop)
             if (kotlin.math.abs(myBottom - tBottom) < threshold) guideOverlay.addLine(kotlin.math.min(myLeft, tLeft), tBottom, kotlin.math.max(myRight, tRight), tBottom)
             if (kotlin.math.abs(myCenterY - tCenterY) < threshold) guideOverlay.addLine(kotlin.math.min(myLeft, tLeft), tCenterY, kotlin.math.max(myRight, tRight), tCenterY)
        }
    }

    private fun checkDeleteZoneHover(screenX: Float, screenY: Float, view: View) {
        val locations = IntArray(2)
        dropDeleteZone.getLocationOnScreen(locations)
        val zoneRect = Rect(locations[0], locations[1], locations[0]+dropDeleteZone.width, locations[1]+dropDeleteZone.height)
        
        if (zoneRect.contains(screenX.toInt(), screenY.toInt())) {
             dropDeleteZone.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100).start()
             view.alpha = 0.3f
        } else {
             dropDeleteZone.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
             view.alpha = 0.8f
        }
    }

    private fun findLabelView(view: View?): TextView? {
        if (view == null) return null
        val expectedTag = "LABEL_FOR_${view.id}"
        return canvasCanvas.findViewWithTag<TextView>(expectedTag)
    }
}
