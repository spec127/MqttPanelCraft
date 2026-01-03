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
    
    init {
        setupUI()
    }
    
    private fun setupUI() {
        val rvLogs = rootView.findViewById<RecyclerView>(R.id.rvConsoleLogs) ?: return
        rvLogs.layoutManager = LinearLayoutManager(rootView.context)
        rvLogs.adapter = logAdapter
        
        // Subscribe Button
        rootView.findViewById<Button>(R.id.btnConsoleSubscribe)?.setOnClickListener {
             val etTopic = rootView.findViewById<EditText>(R.id.etConsoleTopic)
             val topic = etTopic?.text?.toString() ?: ""
             if (topic.isNotEmpty()) {
                 val intent = Intent(rootView.context, MqttService::class.java).apply {
                     action = "SUBSCRIBE"
                     putExtra("TOPIC", topic)
                 }
                 rootView.context.startService(intent)
                 addLog("Subscribed: $topic")
             }
        }
        
        // Send Button
        rootView.findViewById<Button>(R.id.btnConsoleSend)?.setOnClickListener {
             val etPayload = rootView.findViewById<EditText>(R.id.etConsolePayload)
             val payload = etPayload?.text?.toString() ?: ""
             val etTopic = rootView.findViewById<EditText>(R.id.etConsoleTopic)
             val topic = etTopic?.text?.toString() ?: ""
             
             if (topic.isNotEmpty()) {
                 val intent = Intent(rootView.context, MqttService::class.java).apply {
                     action = "PUBLISH"
                     putExtra("TOPIC", topic)
                     putExtra("PAYLOAD", payload)
                 }
                 rootView.context.startService(intent)
                 addLog("Pub: $topic -> $payload")
             } else {
                 Toast.makeText(rootView.context, "Enter Topic to Publish", Toast.LENGTH_SHORT).show()
             }
        }
    }
    
    fun addLog(message: String) {
        logAdapter.addLog(message)
        // Auto scroll?
        val rvLogs = rootView.findViewById<RecyclerView>(R.id.rvConsoleLogs)
        rvLogs?.smoothScrollToPosition(0)
    }
}
