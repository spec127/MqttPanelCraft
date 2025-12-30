package com.example.mqttpanelcraft.model

data class Project(
    val id: String,
    val name: String,
    val broker: String,
    val port: Int = 1883,
    val username: String = "",
    val password: String = "",
    val clientId: String = "",
    val type: ProjectType,
    val isConnected: Boolean = false,
    val components: MutableList<ComponentData> = mutableListOf(),
    val customCode: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var lastOpenedAt: Long = System.currentTimeMillis()
)

enum class ProjectType {
    HOME, FACTORY, WEBVIEW, OTHER
}
