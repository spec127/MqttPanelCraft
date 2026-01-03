package com.example.mqttpanelcraft.utils

import com.example.mqttpanelcraft.model.Project

object HtmlTemplates {
    fun generateDefaultHtml(project: Project?): String {
        val baseTopic = if (project != null) "${project.name}/${project.id}" else "test/project"
        
        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 20px; background: #f5f5f5; color: #333; }
.card { background: white; padding: 20px; border-radius: 12px; margin-bottom: 15px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
h2 { color: #6200EE; }
h3 { margin-top: 0; font-size: 1.1em; color: #555; }
button { padding: 12px 24px; background: #6200EE; color: white; border: none; border-radius: 6px; font-weight: bold; cursor: pointer; transition: background 0.3s; }
button:active { background: #3700B3; }
input { padding: 10px; border: 1px solid #ddd; border-radius: 6px; width: 100%; box-sizing: border-box; margin-bottom: 10px; }
.val-display { margin-top: 10px; font-size: 0.9em; color: #666; font-family: monospace; }
.topic-hint { font-size: 0.8em; color: #999; display: block; margin-bottom: 5px; }
</style>
</head>
<body>

<h2>MQTT Panel WebView</h2>
<div id="status" class="card">Status: Waiting for message...</div>

<!-- Switch Example -->
<div class="card">
  <h3>Switch Control (Index 1)</h3>
  <span class="topic-hint">Topic: $baseTopic/switch/1/set</span>
  <button onclick="sendSwitch('1')">ON</button>
  <button onclick="sendSwitch('0')" style="background:#888;">OFF</button>
  <div id="val_sw" class="val-display">Value: -</div>
</div>

<!-- Text Example -->
<div class="card">
  <h3>Text Display (Index 1)</h3>
  <span class="topic-hint">Topic: $baseTopic/text/1/set</span>
  <input type="text" id="input_text" placeholder="Type message...">
  <button onclick="sendText()">Send Text</button>
  <div id="val_text" class="val-display">Value: -</div>
</div>

<script>
// Base Topic from App Logic
const BASE = "$baseTopic"; 

function mqttOnMessage(topic, payload) {
    document.getElementById('status').innerText = "RX: " + topic + " -> " + payload;
    
    // Switch Feedback (expecting .../switch/1/val OR set)
    if(topic.includes("/switch/1/val") || topic.includes("/switch/1/set")) {
        document.getElementById('val_sw').innerText = "Value: " + payload;
    }
    
    // Text Feedback (expecting .../text/1/val OR set)
    if(topic.includes("/text/1/val") || topic.includes("/text/1/set")) {
        document.getElementById('val_text').innerText = "Value: " + payload;
    }
}

function sendSwitch(state) {
    // Topic: {name}/{id}/switch/1/set
    mqtt.publish(BASE + "/switch/1/set", state);
}

function sendText() {
    var txt = document.getElementById('input_text').value;
    // Topic: {name}/{id}/text/1/set
    mqtt.publish(BASE + "/text/1/set", txt);
}

// Optional: Subscribe explicitly if needed, though App usually subscribes to #
// mqtt.subscribe(BASE + "/+/+/val");
</script>

</body>
</html>
        """.trimIndent()
    }
}
