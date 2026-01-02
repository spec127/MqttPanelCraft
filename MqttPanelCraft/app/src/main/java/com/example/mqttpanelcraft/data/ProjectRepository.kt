package com.example.mqttpanelcraft.data

import android.content.Context
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.model.ComponentData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ProjectRepository {
    private val projects = mutableListOf<Project>()
    private var file: File? = null
    private const val FILE_NAME = "projects.json"

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
                val customCode = obj.optString("customCode", "")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val lastOpenedAt = obj.optLong("lastOpenedAt", System.currentTimeMillis())

                val project = Project(id, name, broker, port, user, pass, client, type, false, mutableListOf(), customCode, createdAt, lastOpenedAt)

                // Load Components
                val compsArray = obj.optJSONArray("components")
                if (compsArray != null) {
                    for (k in 0 until compsArray.length()) {
                        val cObj = compsArray.getJSONObject(k)
                        val cId = cObj.getInt("id")
                        val cType = cObj.getString("type")
                        val cX = cObj.getDouble("x").toFloat()
                        val cY = cObj.getDouble("y").toFloat()
                        val cW = cObj.getInt("width")
                        val cH = cObj.getInt("height")
                        val cLabel = cObj.optString("label", "")
                        val cTopicConfig = cObj.optString("topicConfig", "")

                        val cProps = mutableMapOf<String, String>()
                        val propsObj = cObj.optJSONObject("props")
                        if (propsObj != null) {
                            for (key in propsObj.keys()) {
                                cProps[key] = propsObj.getString(key)
                            }
                        }

                        val comp = ComponentData(cId, cType, cX, cY, cW, cH, cLabel, cTopicConfig, cProps)
                        project.components.add(comp)
                    }
                }

                projects.add(project)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveProjects() {
        if (file == null) {
            android.util.Log.e("ProjectRepo", "saveProjects: File is null!")
            return
        }
        try {
            val jsonArray = JSONArray()
            android.util.Log.d("ProjectRepo", "Saving ${projects.size} projects...")
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
                obj.put("customCode", p.customCode)
                obj.put("createdAt", p.createdAt)
                obj.put("lastOpenedAt", p.lastOpenedAt)

                // Save Components
                val compsArray = JSONArray()
                android.util.Log.d("ProjectRepo", "Saving Project [${p.id}] with ${p.components.size} components.")
                for (c in p.components) {
                    val cObj = JSONObject()
                    cObj.put("id", c.id)
                    cObj.put("type", c.type)
                    cObj.put("x", c.x)
                    cObj.put("y", c.y)
                    cObj.put("width", c.width)
                    cObj.put("height", c.height)
                    cObj.put("label", c.label)
                    cObj.put("topicConfig", c.topicConfig)
                    val propsObj = JSONObject()
                    for ((k, v) in c.props) {
                        propsObj.put(k, v)
                    }
                    cObj.put("props", propsObj)
                    compsArray.put(cObj)
                }
                obj.put("components", compsArray)

                jsonArray.put(obj)
            }
            file!!.writeText(jsonArray.toString())
            android.util.Log.d("ProjectRepo", "Saved successfully to ${file!!.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("ProjectRepo", "Error saving projects", e)
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
        // Safety: Ensure ID is unique. If collision, auto-generate new ID.
        var finalProject = project
        if (getProjectById(project.id) != null) {
            val newId = generateId()
            android.util.Log.w("Repo", "Duplicate ID detected [${project.id}]. Re-assigning to [$newId].")
            finalProject = project.copy(id = newId)
        }
        projects.add(finalProject)
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

    // === Import / Export Logic ===

    fun exportProjectToJson(project: Project): String {
        try {
            val root = JSONObject()
            root.put("schemaVersion", 1)

            val meta = JSONObject()
            meta.put("exportedAt", System.currentTimeMillis())
            meta.put("appVersion", "1.0")
            root.put("meta", meta)

            val pObj = JSONObject()
            // Optional: Export Name? User might want to rename on import. Let's export it as suggestion.
            pObj.put("name", project.name)
            pObj.put("id", project.id) // Export ID
            pObj.put("broker", project.broker)
            pObj.put("port", project.port)
            pObj.put("username", project.username)
            // SECURITY: Do NOT export password by default
            // pObj.put("password", project.password)
            pObj.put("type", project.type.name)
            pObj.put("customCode", project.customCode)
            pObj.put("createdAt", project.createdAt)
            pObj.put("lastOpenedAt", project.lastOpenedAt)

            val compsArray = JSONArray()
            for (c in project.components) {
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("type", c.type)
                cObj.put("x", c.x.toDouble()) // Ensure double for precision
                cObj.put("y", c.y.toDouble())
                cObj.put("width", c.width)
                cObj.put("height", c.height)
                cObj.put("label", c.label)
                cObj.put("topicConfig", c.topicConfig)
                val propsObj = JSONObject()
                for ((k, v) in c.props) {
                    propsObj.put(k, v)
                }
                cObj.put("props", propsObj)
                compsArray.put(cObj)
            }
            pObj.put("components", compsArray)

            root.put("project", pObj)

            // Format with indentation = 2 for readability
            return root.toString(2)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    // Returns a "Template" Project. ID will be empty or temp. Caller must assign real ID and Name.
    fun parseProjectJson(jsonStr: String): Project? {
        try {
            val root = JSONObject(jsonStr)
            // schemaVersion check could go here

            val pObj = root.getJSONObject("project")

            val id = pObj.optString("id", "")
            val name = pObj.optString("name", "Imported Project")
            val broker = pObj.optString("broker", "")
            val port = pObj.optInt("port", 1883)
            val user = pObj.optString("username", "")
            // Password usually valid empty on import
            val typeStr = pObj.optString("type", "HOME")
            val type = try { ProjectType.valueOf(typeStr) } catch (e: Exception) { ProjectType.HOME }
            val customCode = pObj.optString("customCode", "")

            val project = Project(
                id = id,
                name = name,
                broker = broker,
                port = port,
                username = user,
                password = "",
                type = type,
                customCode = customCode,
                createdAt = pObj.optLong("createdAt", System.currentTimeMillis()),
                lastOpenedAt = pObj.optLong("lastOpenedAt", System.currentTimeMillis())
            )

            val compsArray = pObj.optJSONArray("components")
            if (compsArray != null) {
                for (k in 0 until compsArray.length()) {
                     val cObj = compsArray.getJSONObject(k)
                     val cId = cObj.getInt("id")
                     val cType = cObj.getString("type")
                     val cX = cObj.getDouble("x").toFloat()
                     val cY = cObj.getDouble("y").toFloat()
                     val cW = cObj.getInt("width")
                     val cH = cObj.getInt("height")
                     val cLabel = cObj.optString("label", "")
                     val cTopicConfig = cObj.optString("topicConfig", "")

                     val cProps = mutableMapOf<String, String>()
                     val propsObj = cObj.optJSONObject("props")
                     if (propsObj != null) {
                         for (key in propsObj.keys()) {
                             cProps[key] = propsObj.getString(key)
                         }
                     }

                     project.components.add(ComponentData(cId, cType, cX, cY, cW, cH, cLabel, cTopicConfig, cProps))
                }
            }
            return project

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    fun swapProjects(fromIndex: Int, toIndex: Int) {
        if (fromIndex in projects.indices && toIndex in projects.indices) {
            java.util.Collections.swap(projects, fromIndex, toIndex)
            saveProjects()
        }
    }

    fun sortProjects(comparator: Comparator<Project>) {
        java.util.Collections.sort(projects, comparator)
        saveProjects()
    }
}
