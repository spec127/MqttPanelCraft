package com.example.mqttpanelcraft.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.mqttpanelcraft.MqttRepository
import com.example.mqttpanelcraft.MqttStatus
import com.example.mqttpanelcraft.R
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import kotlinx.coroutines.*

class MqttService : Service() {

    private val CHANNEL_ID = "MqttServiceChannel"
    private var mqttClient: MqttClient? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        // Always start foreground first
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MqttPanelCraft")
            .setContentText("MQTT Connection Active")
            .setSmallIcon(R.mipmap.ic_launcher) // Adjust if icon differs
            .setOngoing(true)
            .build()
        startForeground(1, notification)

        if (action == "CONNECT") {
            val broker = intent.getStringExtra("BROKER")
            val port = intent.getIntExtra("PORT", 1883)
            val user = intent.getStringExtra("USER")
            val pass = intent.getStringExtra("PASSWORD")
            val clientId = intent.getStringExtra("CLIENT_ID") ?: ("Android_" + System.currentTimeMillis())
            
            connect(broker, port, user, pass, clientId)
        } else if (action == "DISCONNECT") {
            disconnect()
        } else if (action == "SUBSCRIBE") {
            val topic = intent.getStringExtra("TOPIC")
            subscribe(topic)
        } else if (action == "UNSUBSCRIBE") {
            val topic = intent.getStringExtra("TOPIC")
            unsubscribe(topic)
        } else if (action == "PUBLISH") {
            val topic = intent.getStringExtra("TOPIC")
            val payload = intent.getStringExtra("PAYLOAD")
            publish(topic, payload)
        }

        return START_NOT_STICKY
    }

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.Job())

    private fun connect(broker: String?, port: Int, user: String?, pass: String?, clientId: String) {
        if (broker.isNullOrEmpty()) return

        serviceScope.launch {
            MqttRepository.setStatus(com.example.mqttpanelcraft.MqttStatus.CONNECTING) // Gray
            
            var attempt = 1
            val maxAttempts = 5 // v28: Increased to 5
            var connected = false
            
            while (attempt <= maxAttempts && !connected) {
                try {
                    val uri = "tcp://$broker:$port"
                    // Disconnect existing if any
                    if (mqttClient != null && mqttClient!!.isConnected) {
                        try { mqttClient!!.disconnect() } catch (e: Exception) {}
                    }

                    var finalClientId = clientId
                    var finalCleanSession = false
                    
                    if (clientId.isEmpty()) {
                        finalClientId = "Android_" + System.currentTimeMillis()
                        finalCleanSession = true // Random ID must use Clean Session
                    }

                    mqttClient = MqttClient(uri, finalClientId, MemoryPersistence())
                    val options = MqttConnectOptions()
                    options.isCleanSession = finalCleanSession
                    options.connectionTimeout = 10
                    options.keepAliveInterval = 60
                    options.isAutomaticReconnect = false 
                    
                    if (!user.isNullOrEmpty()) {
                        options.userName = user
                        options.password = pass?.toCharArray()
                    }

                    mqttClient!!.setCallback(object : MqttCallbackExtended {
                        override fun connectionLost(cause: Throwable?) {
                            MqttRepository.addLog("Service: Connection Lost (${cause?.message})", getTime())
                            MqttRepository.setStatus(MqttStatus.CONNECTING) // Gray/Disconnected
                        }

                        override fun messageArrived(topic: String?, message: MqttMessage?) {
                            val payload = message?.toString() ?: ""
                            MqttRepository.processMessage(topic, payload, getTime())
                        }

                        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                             MqttRepository.addLog("Service: Connected to $serverURI (Re: $reconnect)", getTime())
                             MqttRepository.setStatus(MqttStatus.CONNECTED) // Green
                        }
                    })

                    MqttRepository.addLog("Service: Connecting to $uri... (Attempt $attempt/$maxAttempts)", getTime())
                    mqttClient!!.connect(options)
                    
                    connected = true
                    MqttRepository.setStatus(MqttStatus.CONNECTED) // Green
                    MqttRepository.mqttClient = mqttClient

                } catch (e: Exception) {
                    MqttRepository.addLog("Connect Fail ($attempt): ${e.message}", getTime())
                    if (attempt < maxAttempts) {
                        MqttRepository.addLog("Retrying in 3s...", getTime())
                        delay(3000)
                    }
                    attempt++
                }
            }
            
            if (!connected) {
                MqttRepository.addLog("Connection Failed after $maxAttempts attempts.", getTime())
                MqttRepository.setStatus(MqttStatus.FAILED) // Red
            }
        }
    }

    private fun disconnect() {
        try {
            mqttClient?.disconnect()
            MqttRepository.addLog("Service: Disconnected", getTime())
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun subscribe(topic: String?) {
        if (topic.isNullOrEmpty() || mqttClient == null || !mqttClient!!.isConnected) return
        try {
            mqttClient!!.subscribe(topic)
            MqttRepository.addLog("Service: Subscribed to $topic", getTime())
        } catch (e: Exception) {
             MqttRepository.addLog("Service: Subscribe Error - ${e.message}", getTime())
        }
    }
    
    private fun unsubscribe(topic: String?) {
        if (topic.isNullOrEmpty() || mqttClient == null || !mqttClient!!.isConnected) return
        try {
            mqttClient!!.unsubscribe(topic)
            MqttRepository.addLog("Service: Unsubscribed from $topic", getTime())
        } catch (e: Exception) {
             MqttRepository.addLog("Service: Unsubscribe Error - ${e.message}", getTime())
        }
    }

    private fun publish(topic: String?, payload: String?) {
        if (topic.isNullOrEmpty() || payload == null || mqttClient == null || !mqttClient!!.isConnected) return
        try {
            val message = MqttMessage(payload.toByteArray())
            mqttClient!!.publish(topic, message)
             MqttRepository.addLog("Service TX [$topic]: $payload", getTime())
        } catch (e: Exception) {
             MqttRepository.addLog("Service: Publish Error - ${e.message}", getTime())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "MQTT Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
    
    private fun getTime(): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try { mqttClient?.disconnect() } catch (e: Exception) {}
    }
}
