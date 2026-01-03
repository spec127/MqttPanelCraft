package com.example.mqttpanelcraft.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.service.MqttService
import com.google.android.material.slider.Slider

/**
 * ComponentBehaviorManager
 * 負責管理所有元件的行為邏輯 (點擊、滑動、MQTT 更新)。
 * 透過 Strategy 模式 (或簡易的 when 分派) 將邏輯從 Activity 抽離。
 */
class ComponentBehaviorManager(
    private val context: Context,
    private val projectIdProvider: () -> String?,
    private val projectNameProvider: () -> String?,
    private val isEditModeProvider: () -> Boolean,
    private val onImagePickerRequested: (Int) -> Unit // Callback 回 Activity 開啟相簿
) {

    /**
     * 為元件綁定互動邏輯 (Button Click, Slider Change etc.)
     */
    fun attachBehavior(view: View, component: ComponentData) {
        // 基本檢查
        if (view !is FrameLayout || view.childCount == 0) return
        val content = view.getChildAt(0)
        
        val topic = component.topicConfig.ifEmpty { 
             "${projectNameProvider() ?: "project"}/${component.type}/${component.id}"
        }

        when (component.type) {
            "BUTTON" -> {
                if (content is Button) {
                    content.setOnClickListener {
                        if (!isEditModeProvider()) {
                            publishMqtt(topic, "1")
                        }
                    }
                }
            }
            "SLIDER" -> {
                if (content is Slider) {
                    content.addOnChangeListener { _, value, fromUser ->
                        if (fromUser && !isEditModeProvider()) {
                            publishMqtt(topic, value.toInt().toString())
                        }
                    }
                }
            }
            "CAMERA" -> {
                // Camera 元件通常是一個按鈕觸發選圖
                if (content is Button) {
                    content.setOnClickListener {
                        if (!isEditModeProvider()) {
                             onImagePickerRequested(component.id)
                        }
                    }
                }
            }
            "IMAGE" -> {
                // IMAGE 元件有自帶的清除按鈕
                view.findViewWithTag<View>("CLEAR_BTN")?.setOnClickListener {
                    (content as? ImageView)?.setImageResource(android.R.drawable.ic_menu_gallery)
                    // TODO: 是否也要清除 MQTT Retained Message? 目前僅清除 UI
                }
            }
            // TEXT, LED, GRAPH 等通常只有顯示邏輯，無互動邏輯
        }
    }

    /**
     * 更新元件顯示 (當收到 MQTT 訊息時)
     */
    fun updateViewFromMqtt(view: View, component: ComponentData, topic: String, payload: String) {
        // 驗證 Topic 是否匹配
        // 這裡支援簡單的 Topic 比對。若 Component Config 設定了 "home/temp"，則必須完全符合
        // 若 Component Config 是預設空，則系統預設 topic 為 "ProjectName/Type/ID"
        // 這裡傳入的 topic 是實際收到訊息的 topic
        
        val targetTopic = component.topicConfig.ifEmpty { 
            "${projectNameProvider() ?: "project"}/${component.type}/${component.id}"
        }
        
        // 檢查 Topic 匹配 (或是尾綴匹配，相容舊邏輯)
        // 舊邏輯: topic.endsWith("/$id")
        // 新邏輯: 嚴格匹配優先
        val isMatch = (topic == targetTopic) || (topic.endsWith("/${component.id}"))
        
        if (!isMatch) return

        if (view !is FrameLayout || view.childCount == 0) return
        val content = view.getChildAt(0)

        when (component.type) {
            "TEXT" -> {
                if (content is TextView) {
                    content.text = payload
                    // 可以擴充: 依據數值改變顏色
                }
            }
            "LED" -> {
                if (content is View) {
                     // 1/true = Green, 0/false = Red
                     val isOn = payload == "1" || payload.equals("true", ignoreCase = true)
                     val color = if (isOn) Color.GREEN else Color.RED
                     (content.background as? android.graphics.drawable.GradientDrawable)?.setColor(color)
                }
            }
            "THERMOMETER", "GAUGE" -> {
                 // Future: 更新圖表或指針
            }
            // BUTTON/SLIDER 通常不需要因為收到訊息而更新自己 (避免迴圈)，除非是狀態同步
            // 若需要雙向同步，可在這裡實作:
            "SLIDER" -> {
                 if (content is Slider && !content.isPressed) { // 避免使用者拖曳時跳動
                     payload.toFloatOrNull()?.let { content.value = it }
                 }
            }
        }
    }

    private fun publishMqtt(topic: String, payload: String) {
        val intent = Intent(context, MqttService::class.java).apply {
            action = "PUBLISH"
            putExtra("TOPIC", topic)
            putExtra("PAYLOAD", payload)
        }
        context.startService(intent)
    }
}
