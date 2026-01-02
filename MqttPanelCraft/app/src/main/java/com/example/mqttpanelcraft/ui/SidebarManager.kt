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
    fun setupComponentPalette(view: View) {
        // Find buttons/icons in the sidebar view and set OnLongClickListener
        // to trigger drag events.
        // Example:
        // view.findViewById<View>(R.id.icon_switch).setOnLongClickListener { ... }
    }
}
