package com.example.mqttpanelcraft

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    private val items = mutableListOf<String>()

    fun submitList(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val text = items[position]
        holder.textView.text = text
        holder.textView.textSize = 12f
        holder.textView.maxLines = 3
        holder.textView.ellipsize = android.text.TextUtils.TruncateAt.END
        
        // Simple logic to colorize RX/TX
        when {
            text.contains("RX [") -> holder.textView.setTextColor(0xFF388E3C.toInt()) // Green
            text.contains("TX [") -> holder.textView.setTextColor(0xFF1976D2.toInt()) // Blue
            else -> holder.textView.setTextColor(holder.itemView.context.getColor(R.color.sidebar_text_primary)) // Theme aware
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)
    }
}
