package com.example.mqttpanelcraft.data

import android.content.Context
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ProjectRepository {
    private val projects = mutableListOf<Project>()
    private var file: File? = null

    fun initialize(context: Context) {
        file = File(context.filesDir, "projects.json")
        loadProjects()
    }

    private fun loadProjects() {
        projects.clear()
        if (file == null || !file!!.exists()) {
             // Defaults if empty
            projects.add(Project("1", "Smart Home", "broker.emqx.io", 1883, "", "", "", ProjectType.HOME, false))
            projects.add(Project("2", "Office Env", "test.mosquitto.org", 1883, "", "", "", ProjectType.FACTORY, false))
            saveProjects() 
            return
        }

        try {
            val jsonStr = file!!.readText()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val broker = obj.getString("broker")
                val port = obj.optInt("port", 1883)
                val user = obj.optString("username", "")
                val pass = obj.optString("password", "")
                val client = obj.optString("clientId", "")
                val typeStr = obj.optString("type", "HOME")
                val type = try { ProjectType.valueOf(typeStr) } catch (e: Exception) { ProjectType.HOME }
                
                projects.add(Project(id, name, broker, port, user, pass, client, type, false))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveProjects() {
        if (file == null) return
        try {
            val jsonArray = JSONArray()
            for (p in projects) {
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("broker", p.broker)
                obj.put("port", p.port)
                obj.put("username", p.username)
                obj.put("password", p.password)
                obj.put("clientId", p.clientId)
                obj.put("type", p.type.name)
                jsonArray.put(obj)
            }
            file!!.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAllProjects(): List<Project> {
        return projects.toList()
    }
    
    fun getProjectById(id: String): Project? {
        return projects.find { it.id == id }
    }

    fun addProject(project: Project) {
        projects.add(project)
        saveProjects()
    }
    
    fun updateProject(updatedProject: Project) {
        val index = projects.indexOfFirst { it.id == updatedProject.id }
        if (index != -1) {
            projects[index] = updatedProject
            saveProjects()
        }
    }
    
    fun deleteProject(id: String) {
        projects.removeIf { it.id == id }
        saveProjects()
    }
    
    fun generateId(): String {
        val secureRandom = java.security.SecureRandom()
        val charPool = "0123456789abcdefghijklmnopqrstuvwxyz"
        var newId: String
        do {
            newId = (1..10)
                .map { charPool[secureRandom.nextInt(charPool.length)] }
                .joinToString("")
        } while (getProjectById(newId) != null)
        return newId
    }

    fun isProjectNameTaken(name: String, excludeId: String? = null): Boolean {
        return projects.any {
            it.name.equals(name, ignoreCase = true) && it.id != excludeId
        }
    }
}
