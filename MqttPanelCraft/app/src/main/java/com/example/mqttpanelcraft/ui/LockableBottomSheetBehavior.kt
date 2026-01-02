package com.example.mqttpanelcraft.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.example.mqttpanelcraft.R
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Custom BottomSheetBehavior.
 * Rules:
 * 1. Dragging is ONLY allowed from the Header View.
 * 2. Touching content does NOT drag the sheet.
 * 3. Nested scrolling in content is handled naturally but doesn't affect sheet position unless at edges (standard behavior but we want to restrict it).
 */
class LockableBottomSheetBehavior<V : View> : BottomSheetBehavior<V> {
    
    var headerViewId: Int = 0
    private var headerView: View? = null
    var isLocked: Boolean = true // Now always TRUE effectively based on user request

    constructor() : super()
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    
    // Helper to check if touch is inside header
    private fun isTouchInHeader(event: MotionEvent): Boolean {
        // Try to find header if null
        if (headerView == null && headerViewId != 0) {
             // We can't easily findById from here without a reference view, 
             // but we can try in onInterceptTouchEvent's parent or child
             return false 
        }
        
        val header = headerView ?: return false
        val location = IntArray(2)
        header.getLocationOnScreen(location)
        
        val x = location[0]
        val y = location[1]
        val w = header.width
        val h = header.height
        
        val touchX = event.rawX
        val touchY = event.rawY
        
        return touchX >= x && touchX <= (x + w) && touchY >= y && touchY <= (y + h)
    }

    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {
        // Strict Lock: If hidden and locked (Edit Mode), do not allow ANY interaction to drag it out.
        // It must be opened programmatically.
        if (isLocked && state == STATE_HIDDEN) {
             return false
        }
    
        // Initialize Header View references if needed
        if (headerView == null && headerViewId != 0) {
            headerView = child.findViewById(headerViewId)
            // Fallback: try finding in parent if child is just content
            if (headerView == null) {
                headerView = parent.findViewById(headerViewId)
            }
        }

        // 1. If Locked matches user request: Only DRAG from Header
        // If event is DOWN or MOVE, we check if it is in header.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (!isTouchInHeader(event)) {
                return false
            }
            // If Locked (Edit Mode), we want to PREVENT Dragging entirely.
            // We only want Click.
            // So we return false here too, effectively disabling the Behavior's drag intervention.
            // The click will still go to the Header View.
            if (isLocked) {
                return false
            }
        }

        return super.onInterceptTouchEvent(parent, child, event)
    }

    override fun onTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {
        // Same logic: If it's a drag attempt not on header, ignore.
        
        if (headerView == null && headerViewId != 0) {
             headerView = parent.findViewById(headerViewId)
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (isLocked) return false
            if (!isTouchInHeader(event)) {
                 return false // Don't handle touch on content
            }
        }
        
        // If we are here, it's either in header OR it's a subsequent MOVE event
        // that started in header.
        // However, standard onTouchEvent handles the actual dragging.
        // We only want to delegate to super if it started in header.
        
        // Tracking "started in header" is tricky across calls without state.
        // IsLocked logic:
        
        return super.onTouchEvent(parent, child, event)
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        // Prevent generic nested scroll from moving the sheet
        return false 
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
         // Consume nothing, let content scroll, but don't move sheet
    }
}
