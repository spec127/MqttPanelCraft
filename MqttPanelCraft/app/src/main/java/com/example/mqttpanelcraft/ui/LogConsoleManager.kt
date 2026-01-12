package com.example.mqttpanelcraft.ui

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.adapter.LogAdapter
import com.example.mqttpanelcraft.service.MqttService

class LogConsoleManager(
    private val rootView: View
) {
    private val logAdapter = LogAdapter()
    private val fullLogs = StringBuilder()
    
    init {
        setupUI()
    }
    
    private fun setupUI() {
        val rvLogs = rootView.findViewById<RecyclerView>(R.id.rvConsoleLogs) ?: return
        rvLogs.layoutManager = LinearLayoutManager(rootView.context)
        rvLogs.adapter = logAdapter
        
        // Topic Adapter
        val atvTopic = rootView.findViewById<android.widget.AutoCompleteTextView>(R.id.etConsoleTopic)
        if (atvTopic != null) {
            topicAdapter = TopicAdapter(rootView.context, emptyList())
            atvTopic.setAdapter(topicAdapter)
            // Show all on click?
            atvTopic.setOnClickListener { atvTopic.showDropDown() }
            atvTopic.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) atvTopic.showDropDown() }
        }
    }
    
    private lateinit var topicAdapter: TopicAdapter
    
    fun updateTopics(components: List<com.example.mqttpanelcraft.model.ComponentData>, project: com.example.mqttpanelcraft.model.Project?) {
        val items = ArrayList<com.example.mqttpanelcraft.model.ComponentData>()
        
        if (project != null) {
             val safeProjName = project.name.lowercase().replace("/", "_").replace(" ", "_").replace("+", "")
             val prefix = "$safeProjName/${project.id}/"
             
             // 1. Add "Custom" Topic (Base Prefix)
             items.add(com.example.mqttpanelcraft.model.ComponentData(
                 id = -999, 
                 type = "CUSTOM", 
                 x = 0f, 
                 y = 0f, 
                 width = 0, 
                 height = 0, 
                 label = "Custom", 
                 topicConfig = prefix
             ))
             
             // 2. Add Prefixed Components
             items.addAll(components.filter { it.topicConfig.startsWith(prefix) })
        } else {
             // Fallback if no project context (shouldn't happen often)
             items.addAll(components)
        }

        if (::topicAdapter.isInitialized) {
            topicAdapter.updateData(items)
        }
        
        // Subscribe Button REMOVED
        
    // Send Button
        rootView.findViewById<Button>(R.id.btnConsoleSend)?.setOnClickListener {
             val etPayload = rootView.findViewById<EditText>(R.id.etConsolePayload)
             val payload = etPayload?.text?.toString() ?: ""
             // AutoCompleteTextView is an EditText
             val etTopic = rootView.findViewById<EditText>(R.id.etConsoleTopic)
             val topic = etTopic?.text?.toString() ?: ""
             
             if (topic.isNotEmpty()) {
                 val intent = Intent(rootView.context, MqttService::class.java).apply {
                     action = "PUBLISH"
                     putExtra("TOPIC", topic)
                     putExtra("PAYLOAD", payload)
                 }
                 rootView.context.startService(intent)
                 // Trigger VM to add log (Will circle back via observation)
                 // But we don't have VM ref here.
                 // We rely on caller or Activity.
                 // Activity handles received messages. But this is SENT.
                 // We should probably expose a callback `onSend`.
                 // For now, let's just let Activity handle logs via Service? Service doesn't log back.
                 // Let's assume user wants to see SENT messages too.
                 // Activity should pass a callback or we just assume logic.
                 // Wait, LogConsoleManager shouldn't update UI directly for local actions if we have VM.
             } else {
                 Toast.makeText(rootView.context, "Enter Topic to Publish", Toast.LENGTH_SHORT).show()
             }
        }
    }
    
    fun setClearAction(action: () -> Unit) {
         rootView.findViewById<View>(R.id.btnLogClear)?.setOnClickListener {
             action()
         }
    }
    
    fun updateLogs(logs: List<String>) {
        // Optimize: If appending, just add. If clear, clear.
        // Simple: clear and add all? (Can be slow for 2000 items)
        // Diff util?
        // Let's rely on Adapter logic.
        logAdapter.setLogs(logs)
        val rvLogs = rootView.findViewById<RecyclerView>(R.id.rvConsoleLogs)
        if (logs.isNotEmpty()) {
            rvLogs?.scrollToPosition(logs.size - 1)
        }
    }

    fun getLogs(): String {
        return logAdapter.getAllLogs()
    }
}
