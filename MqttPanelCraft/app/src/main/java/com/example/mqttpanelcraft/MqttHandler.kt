package com.example.mqttpanelcraft

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttHandler(
    private val context: Context,
    private val onMessageReceived: (topic: String, message: String) -> Unit,
    private val onConnectionStatusChanged: (isConnected: Boolean) -> Unit // New Callback
) {

    private var mqttClient: MqttAsyncClient? = null
    private val subscribedTopics = mutableSetOf<String>()

    fun connect(brokerUrl: String, clientId: String) {
        // Validation
        if (brokerUrl.isBlank()) {
            onConnectionStatusChanged(false)
            return
        }
        
        // Robust Client ID
        val effectiveClientId = if (clientId.isBlank()) {
            "Android_" + System.currentTimeMillis()
        } else {
            clientId
        }

        try {
            // Disconnect/Close existing if needed
            if (mqttClient != null) {
                if (mqttClient!!.isConnected) {
                    onConnectionStatusChanged(true)
                    return // Already connected
                } else {
                    try { mqttClient!!.disconnect() } catch(e:Exception){}
                    try { mqttClient!!.close() } catch(e:Exception){}
                    mqttClient = null
                    onConnectionStatusChanged(false)
                }
            }

            // Ensure brokerUrl format
            val finalUrl = if (!brokerUrl.contains("://")) "tcp://$brokerUrl:1883" else brokerUrl
            
            Log.d("MqttHandler", "Creating client: $finalUrl, $effectiveClientId")

            mqttClient = MqttAsyncClient(finalUrl, effectiveClientId, MemoryPersistence())
            
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = false
                connectionTimeout = 10
                keepAliveInterval = 20
            }

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MqttHandler", "Connect Success")
                    onConnectionStatusChanged(true)
                    resubscribeAll()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MqttHandler", "Connect Failure: ${exception?.message}")
                    onConnectionStatusChanged(false)
                }
            })

            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d("MqttHandler", "Connected to $serverURI")
                    onConnectionStatusChanged(true)
                    if (reconnect) {
                        resubscribeAll()
                    }
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.d("MqttHandler", "Connection Lost")
                    onConnectionStatusChanged(false)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val msg = message?.toString() ?: ""
                   // Log.d("MqttHandler", "Msg: $topic -> $msg") // verbose
                    if (topic != null) {
                        onMessageReceived(topic, msg)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

        } catch (e: Exception) {
            Log.e("MqttHandler", "Fatal Connect Error", e)
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun subscribe(topic: String) {
        if (subscribedTopics.contains(topic)) return
        val client = mqttClient
        if (client != null && client.isConnected) {
            try {
                client.subscribe(topic, 0)
                subscribedTopics.add(topic)
                Log.d("MqttHandler", "Subscribed to: $topic")
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        } else {
            subscribedTopics.add(topic)
        }
    }

    fun publish(topic: String, message: String) {
        val client = mqttClient
        if (client != null && client.isConnected) {
            try {
                val mqttMessage = MqttMessage()
                mqttMessage.payload = message.toByteArray()
                client.publish(topic, mqttMessage)
            } catch (e: MqttException) {
                e.printStackTrace()
                Log.e("MqttHandler", "Publish Failed", e)
            }
        }
    }

    fun isConnected(): Boolean = mqttClient?.isConnected == true
    
    fun resubscribeAll() {
        val client = mqttClient ?: return
        subscribedTopics.forEach { topic ->
             if (client.isConnected) {
                 try {
                     client.subscribe(topic, 0)
                 } catch (e: Exception){}
             }
        }
    }
}
