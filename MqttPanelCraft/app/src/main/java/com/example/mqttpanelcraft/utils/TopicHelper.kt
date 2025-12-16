package com.example.mqttpanelcraft.utils

import com.example.mqttpanelcraft.model.Project
import java.util.Locale

object TopicHelper {

    /**
     * Maps UI tags to standard component types.
     */
    fun getComponentType(tag: String): String {
        return when (tag.uppercase(Locale.ROOT)) {
            "BUTTON" -> "button"
            "SLIDER" -> "slider"
            "LED" -> "led"
            "THERMOMETER" -> "analog"
            "IMAGE" -> "image"
            "TEXT" -> "text"
            else -> "unknown"
        }
    }

    /**
     * Formats the base topic: {projectNameLower}/{projectId}
     */
    fun formatBaseTopic(projectName: String, projectId: String): String {
        val nameLower = projectName.lowercase(Locale.ROOT).replace("\\s".toRegex(), "_")
        // Ensure name only contains allowed chars, though SetupActivity enforces this.
        // We assume valid input here but extra safety doesn't hurt.
        val cleanName = nameLower.replace("[^a-z0-9_]".toRegex(), "")
        return "$cleanName/$projectId"
    }

    /**
     * Formats the full topic for a component.
     * {projectNameLower}/{projectId}/{componentType}/{componentIndex}/{direction}
     */
    fun formatTopic(
        projectName: String,
        projectId: String,
        componentType: String,
        componentIndex: String,
        direction: String = "cmd" // "cmd" or "rep"
    ): String {
        val base = formatBaseTopic(projectName, projectId)
        return "$base/$componentType/$componentIndex/$direction"
    }
}
