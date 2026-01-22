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
                 
                 // Registry only
                 val def = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(tag)
                 val (w, h) = if (def != null) {
                      val density = checkContext.resources.displayMetrics.density
                      Pair((def.defaultSize.width * density).toInt(), (def.defaultSize.height * density).toInt())
                 } else {
                      Pair(300, 300)
                 }

                 // Create temp preview for shadow
                 val previewView = def?.createView(checkContext, true) ?: View(checkContext)
                 
                 // FIX: Apply default styles (Shape, Color, etc.) via onUpdateView
                 if (def != null) {
                     val dummyData = com.example.mqttpanelcraft.model.ComponentData(
                         id = -1,
                         type = tag,
                         x = 0f,
                         y = 0f,
                         width = w,
                         height = h,
                         label = def.labelPrefix,
                         topicConfig = "",
                         props = mutableMapOf()
                     )
                     def.onUpdateView(previewView, dummyData)
                 }

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
        val switchLandscape = rootView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchLockLandscape)

        if (switchPortrait != null && switchLandscape != null) {
            val isPortraitLocked = prefs.getBoolean("lock_portrait", false)
            val isLandscapeLocked = prefs.getBoolean("lock_landscape", false)

            switchPortrait.isChecked = isPortraitLocked
            switchLandscape.isChecked = isLandscapeLocked

            // Apply initial state
            if (isPortraitLocked) activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else if (isLandscapeLocked) activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            switchPortrait.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    switchLandscape.isChecked = false
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    if (!switchLandscape.isChecked) activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                prefs.edit().putBoolean("lock_portrait", isChecked).apply()
            }

            switchLandscape.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    switchPortrait.isChecked = false
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    if (!switchPortrait.isChecked) activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                prefs.edit().putBoolean("lock_landscape", isChecked).apply()
            }
        }
    }
}
