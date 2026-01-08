package com.example.mqttpanelcraft.ui

import android.view.View
import android.widget.FrameLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.mqttpanelcraft.R
import com.google.android.material.navigation.NavigationView

class SidebarManager(
    private val drawerLayout: DrawerLayout?,
    private val propertyContainer: View?,
    private val componentContainer: View?,
    private val runModeContainer: View?,
    private val onComponentDragStart: (View, String) -> Unit // Callback when dragging from sidebar
) {

    fun showPropertiesPanel() {
        propertyContainer?.visibility = View.VISIBLE
        componentContainer?.visibility = View.GONE
        runModeContainer?.visibility = View.GONE
        // openDrawer() // User requested NO auto-open
    }

    fun showComponentsPanel() {
        propertyContainer?.visibility = View.GONE
        componentContainer?.visibility = View.VISIBLE
        runModeContainer?.visibility = View.GONE
        // openDrawer() // User requested NO auto-open
    }

    fun showRunModePanel() {
        propertyContainer?.visibility = View.GONE
        componentContainer?.visibility = View.GONE
        runModeContainer?.visibility = View.VISIBLE
        // openDrawer() // User requested NO auto-open
    }

    fun openDrawer() {
        if (drawerLayout?.isDrawerOpen(GravityCompat.START) == false) {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    fun closeDrawer() {
        if (drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }
    
    fun isDrawerOpen(): Boolean {
        return drawerLayout?.isDrawerOpen(GravityCompat.START) == true
    }

    // Setup listeners for component palette items (to be implemented fully)
    fun setupComponentPalette(rootView: View) {
        val categories = listOf(
            R.id.cardText to "TEXT",
            R.id.cardImage to "IMAGE",
            R.id.cardButton to "BUTTON",
            R.id.cardSlider to "SLIDER",
            R.id.cardLed to "LED",
            R.id.cardThermometer to "THERMOMETER",
            R.id.cardCamera to "CAMERA"
        )
        
        val touchListener = View.OnTouchListener { view, event ->
             if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                 val tag = view.tag as? String ?: return@OnTouchListener false
                 
                 val item = android.content.ClipData.Item(tag)
                 val dragData = android.content.ClipData(tag, arrayOf(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN), item)
                 
                 // Generate "Real" Preview View for Shadow
                 val checkContext = view.context
                 val (w, h) = ComponentFactory.getDefaultSize(checkContext, tag)
                 
                 // Create temp preview for shadow
                 val previewView = ComponentFactory.createComponentView(checkContext, tag, true)
                 
                 // Measure and Layout manually for Shadow
                 val widthSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
                 val heightSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                 previewView.measure(widthSpec, heightSpec)
                 previewView.layout(0, 0, w, h)
                 
                 val shadow = object : View.DragShadowBuilder(previewView) {
                     override fun onProvideShadowMetrics(outShadowSize: android.graphics.Point, outShadowTouchPoint: android.graphics.Point) {
                         outShadowSize.set(previewView.measuredWidth, previewView.measuredHeight)
                         outShadowTouchPoint.set(previewView.measuredWidth / 2, previewView.measuredHeight / 2)
                     }
                     override fun onDrawShadow(canvas: android.graphics.Canvas) {
                         previewView.draw(canvas)
                     }
                 }

                 view.startDragAndDrop(dragData, shadow, null, 0)
                 closeDrawer()
                 return@OnTouchListener true
             }
             false
        }

        categories.forEach { (id, tag) ->
            rootView.findViewById<View>(id)?.apply {
                this.tag = tag
                setOnTouchListener(touchListener)
            }
        }
    }
    
    fun setupRunModeSettings(rootView: View, activity: android.app.Activity) {
        val prefs = activity.getSharedPreferences("ProjectViewPrefs", android.content.Context.MODE_PRIVATE)

        // Orientation Control
        val switchPortrait = rootView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchLockPortrait)
        val switchLandscapeLeft = rootView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchLockLandscapeLeft)
        val switchLandscapeRight = rootView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchLockLandscapeRight)

        if (switchPortrait != null && switchLandscapeLeft != null && switchLandscapeRight != null) {
            val isPortraitLocked = prefs.getBoolean("lock_portrait", false)
            val isLandscapeLeftLocked = prefs.getBoolean("lock_landscape_left", false)
            val isLandscapeRightLocked = prefs.getBoolean("lock_landscape_right", false)

            switchPortrait.isChecked = isPortraitLocked
            switchLandscapeLeft.isChecked = isLandscapeLeftLocked
            switchLandscapeRight.isChecked = isLandscapeRightLocked

            // Apply initial state
            if (isPortraitLocked) {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else if (isLandscapeLeftLocked) {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            } else if (isLandscapeRightLocked) {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            switchPortrait.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    switchLandscapeLeft.isChecked = false
                    switchLandscapeRight.isChecked = false
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    if (!switchLandscapeLeft.isChecked && !switchLandscapeRight.isChecked) {
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
                prefs.edit().putBoolean("lock_portrait", isChecked).apply()
            }

            switchLandscapeLeft.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    switchPortrait.isChecked = false
                    switchLandscapeRight.isChecked = false
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                } else {
                    if (!switchPortrait.isChecked && !switchLandscapeRight.isChecked) {
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
                prefs.edit().putBoolean("lock_landscape_left", isChecked).apply()
            }

            switchLandscapeRight.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    switchPortrait.isChecked = false
                    switchLandscapeLeft.isChecked = false
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    if (!switchPortrait.isChecked && !switchLandscapeLeft.isChecked) {
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
                prefs.edit().putBoolean("lock_landscape_right", isChecked).apply()
            }
        }
    }
}
