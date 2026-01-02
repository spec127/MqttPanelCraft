package com.example.mqttpanelcraft.ui

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.MqttHandler
import com.example.mqttpanelcraft.adapter.LogAdapter

class LogConsoleManager(
    private val rootView: View,
    private val mqttHandler: MqttHandler
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
                 mqttHandler.subscribe(topic)
                 addLog("Subscribed: $topic")
             }
        }
        
        // Send Button
        rootView.findViewById<Button>(R.id.btnConsoleSend)?.setOnClickListener {
             val etPayload = rootView.findViewById<EditText>(R.id.etConsolePayload)
             val payload = etPayload?.text?.toString() ?: ""
             
             // Topic for Send? Usually reusing the subscribe topic or a separate field?
             // Based on typical UI, if there's only one topic field, we reuse it.
             // Or we check if there is a specific send topic field.
             // Assuming etConsoleTopic is the target for now as typically consoles use one target.
             val etTopic = rootView.findViewById<EditText>(R.id.etConsoleTopic)
             val topic = etTopic?.text?.toString() ?: ""
             
             if (topic.isNotEmpty()) {
                 mqttHandler.publish(topic, payload)
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
