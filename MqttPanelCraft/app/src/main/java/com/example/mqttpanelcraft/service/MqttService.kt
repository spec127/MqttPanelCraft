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

/**
 * MqttService: 背景 MQTT 連線服務 (Background Service).
 * 
 * 負責：
 * 1. 維持 MQTT 長連線 (Long-running Connection) 即使 App 退道背景.
 * 2. 處理自動重連 (Auto Reconnect) 與重試機制.
 * 3. 接收訂閱消息並透過 Repository 更新 UI.
 * 4. 支援前台服務 (Foreground Service) 以避免被系統殺死.
 * 
 * Actions: CONNECT, DISCONNECT, SUBSCRIBE, PUBLISH.
 */
class MqttService : Service() {

    private val CHANNEL_ID = "MqttServiceChannel"
    private var mqttClient: MqttClient? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * 處理 Service 啟動指令.
     * 
     * @param intent 包含 Action 與相關參數 (Topic, Payload, Broker Info).
     * @return START_STICKY 確保 Service 被殺死後嘗試合重啟.
     */
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
        
        // Dispatch Actions
        when (action) {
            "CONNECT" -> {
                val broker = intent.getStringExtra("BROKER")
                val port = intent.getIntExtra("PORT", 1883)
                val user = intent.getStringExtra("USER")
                val pass = intent.getStringExtra("PASSWORD")
                val clientId = intent.getStringExtra("CLIENT_ID") ?: ("Android_" + System.currentTimeMillis())
                connect(broker, port, user, pass, clientId)
            }
            "DISCONNECT" -> disconnect()
            "SUBSCRIBE" -> {
                val topic = intent.getStringExtra("TOPIC")
                subscribe(topic)
            }
            "UNSUBSCRIBE" -> {
                val topic = intent.getStringExtra("TOPIC")
                unsubscribe(topic)
            }
            "PUBLISH" -> {
                val topic = intent.getStringExtra("TOPIC")
                val payload = intent.getStringExtra("PAYLOAD")
                publish(topic, payload)
            }
        }

        return START_STICKY // v30: Changed to STICKY for reliability
    }

    // Coroutine Scope for background network operations
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.Job())

    /**
     * 建立 MQTT 連線.
     * 
     * 包含重試邏輯 (3次嘗試) 與自動重連設定.
     */
    private fun connect(broker: String?, port: Int, user: String?, pass: String?, clientId: String) {
        if (broker.isNullOrEmpty()) return

        serviceScope.launch {
            MqttRepository.setStatus(MqttStatus.CONNECTING) // Gray
            
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
                options.connectionTimeout = 30 // v30: Increased to 30s
                options.keepAliveInterval = 60
                options.isAutomaticReconnect = true // v30: Enable Paho Auto Reconnect
                
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
                         
                         // If we need to re-subscribe after auto-reconnect, we can do it here.
                         // But main activity usually handles logic. Ideally we should track subscriptions.
                         // For now, relies on Activity sending SUBSCRIBE again or CleanSession=false persistence.
                    }
                })

                MqttRepository.addLog("Service: Connecting to $uri...", getTime())
                
                // v43: Limited Retry Logic (3 Attempts)
                var attempts = 0
                val maxAttempts = 3
                var connected = false // v44: Fix unresolved reference
                
                while (!connected && attempts < maxAttempts && isActive) { // v44: Fix isActive usage
                    attempts++
                    try {
                        MqttRepository.addLog("Service: Connecting ($attempts/$maxAttempts)...", getTime())
                        mqttClient!!.connect(options)
                        connected = true
                        MqttRepository.setStatus(MqttStatus.CONNECTED) // Green
                        MqttRepository.mqttClient = mqttClient
                        MqttRepository.addLog("Service: Connected!", getTime())
                    } catch (e: Exception) {
                        if (attempts < maxAttempts) {
                             MqttRepository.addLog("Connect Fail: ${e.message}. Retrying in 3s...", getTime())
                             delay(3000) // 3s delay between attempts
                        } else {
                             // Final Failure
                             MqttRepository.addLog("Connection Failed after $maxAttempts attempts.", getTime())
                             MqttRepository.setStatus(MqttStatus.FAILED)
                        }
                    }
                }

            } catch (e: Exception) {
                 // Setup errors (uri parsing etc)
                MqttRepository.addLog("Fatal Connect Error: ${e.message}", getTime())
                MqttRepository.setStatus(MqttStatus.FAILED) 
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
