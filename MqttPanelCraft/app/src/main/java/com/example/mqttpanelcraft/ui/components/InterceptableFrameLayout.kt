package com.example.mqttpanelcraft.ui.components

import android.content.Context
import android.widget.FrameLayout

// Custom FrameLayout to handle event interception in Edit Mode
// Shared by Legacy Factory and New Definition Architecture
class InterceptableFrameLayout(context: Context) : FrameLayout(context) {
    var isEditMode: Boolean = false

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent?): Boolean {
        // In Edit Mode, we want to intercept all touches so the children (Button/Slider) don't get them.
        // This allows the OnTouchListener on THIS container (dragging) to work.
        if (isEditMode) {
             return true 
        }
        return super.onInterceptTouchEvent(ev)
    }
}
