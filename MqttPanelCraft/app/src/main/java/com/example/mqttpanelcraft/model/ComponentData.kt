package com.example.mqttpanelcraft.model

data class ComponentData(
    var id: Int,
    val type: String,
    var x: Float,
    var y: Float,
    var width: Int,
    var height: Int,
    var label: String,
    var topicConfig: String = "",
    var props: MutableMap<String, String> = mutableMapOf()
)
