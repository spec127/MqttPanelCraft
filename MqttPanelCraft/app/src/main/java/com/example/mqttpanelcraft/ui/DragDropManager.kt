package com.example.mqttpanelcraft.ui

import android.content.ClipData
import android.graphics.Color
import android.view.DragEvent
import android.view.View
import android.widget.TextView

class DragDropManager(
    private val isEditModeProvider: () -> Boolean,
    private val onComponentDeleted: (Int) -> Unit
) {

    fun setupDropZone(dropDeleteZone: View) {
        dropDeleteZone.setOnDragListener { v, event ->
             when (event.action) {
                 DragEvent.ACTION_DRAG_STARTED -> {
                      v.visibility = View.VISIBLE
                      v.animate().alpha(1.0f).setDuration(200).start()
                      true
                 }
                 DragEvent.ACTION_DRAG_ENTERED -> {
                      v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).start()
                      true
                 }
                 DragEvent.ACTION_DRAG_EXITED -> {
                      v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                      true
                 }
                 DragEvent.ACTION_DROP -> {
                     v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                     val draggedView = event.localState as? View
                     if (draggedView != null) {
                         try {
                             // Callback to delete
                             onComponentDeleted(draggedView.id)
                         } catch (e: Exception) {
                            e.printStackTrace()
                         }
                     }
                     v.visibility = View.GONE
                     true
                 }
                 DragEvent.ACTION_DRAG_ENDED -> {
                     v.visibility = View.GONE
                     true
                 }
                 else -> true
             }
        }
    }

    fun attachDragBehavior(view: View, onDragStart: (View) -> Unit) {
        view.setOnLongClickListener { v ->
             if (!isEditModeProvider()) return@setOnLongClickListener false
             
             // Trigger callback (e.g., to show Drop Zone)
             onDragStart(v)
             
             val dragData = ClipData("MOVE", arrayOf(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item("MOVE"))
             val shadow = View.DragShadowBuilder(v)
             v.startDragAndDrop(dragData, shadow, v, 0)
             v.visibility = View.INVISIBLE
             true
        }
        
        view.setOnDragListener { v, event ->
             if (event.action == DragEvent.ACTION_DRAG_ENDED) {
                 v.visibility = View.VISIBLE
             }
             true
        }
    }
}
