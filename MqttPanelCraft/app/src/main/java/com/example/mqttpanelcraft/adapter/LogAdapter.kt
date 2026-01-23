package com.example.mqttpanelcraft.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mqttpanelcraft.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val logs = mutableListOf<String>()

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logs.add("[$timestamp] $message") // Newest Last (Bottom)
        if (logs.size > 100) logs.removeAt(0) // Remove Oldest (Top)
        notifyDataSetChanged() // efficient enough for logs, or notifyItemInserted(logs.size-1) + notifyItemRemoved(0)
    }

    fun setLogs(newLogs: List<String>) {
        logs.clear()
        // ViewModel stores Oldest -> Newest.
        // Adapter displays Oldest -> Newest (Top -> Bottom).
        logs.addAll(newLogs) 
        notifyDataSetChanged()
    }
    
    fun getAllLogs(): String {
        // Return mostly for copy-paste
        return logs.joinToString("\n")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.textView.text = logs[position]
        holder.textView.textSize = 12f
        holder.textView.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.sidebar_text_primary))
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)
    }
}
