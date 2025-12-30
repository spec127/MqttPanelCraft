package com.example.mqttpanelcraft.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Custom BottomSheetBehavior that only responds to touches on a specific header view.
 * This prevents drag-to-close from content area while preserving internal scrolling.
 */
class LockableBottomSheetBehavior<V : View> : BottomSheetBehavior<V> {
    
    var headerViewId: Int = 0
    private var headerView: View? = null
    
    constructor() : super()
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    
    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {
        // Find header view if not cached
        if (headerView == null && headerViewId != 0) {
            headerView = child.findViewById(headerViewId)
        }
        
        // Only intercept touches if they're on the header
        headerView?.let { header ->
            val location = IntArray(2)
            header.getLocationInWindow(location)
            
            val headerTop = location[1]
            val headerBottom = headerTop + header.height
            
            val touchY = event.rawY.toInt()
            
            // If touch is outside header area, don't intercept
            if (touchY < headerTop || touchY > headerBottom) {
                return false
            }
        }
        
        // For header touches or if header not found, use default behavior
        return super.onInterceptTouchEvent(parent, child, event)
    }
    
    override fun onTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {
        // Same logic for touch events
        headerView?.let { header ->
            val location = IntArray(2)
            header.getLocationInWindow(location)
            
            val headerTop = location[1]
            val headerBottom = headerTop + header.height
            
            val touchY = event.rawY.toInt()
            
            // If touch is outside header area, don't handle
            if (touchY < headerTop || touchY > headerBottom) {
                return false
            }
        }
        
        return super.onTouchEvent(parent, child, event)
    }
}
