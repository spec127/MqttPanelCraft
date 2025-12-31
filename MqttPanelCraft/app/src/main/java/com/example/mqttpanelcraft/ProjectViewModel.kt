package com.example.mqttpanelcraft

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.utils.CrashLogger
import java.util.UUID

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    // Repository is a Singleton Object, no instantiation needed
    // private val repository = ProjectRepository(application) 

    private val _project = MutableLiveData<Project?>()
    val project: LiveData<Project?> = _project

    private val _components = MutableLiveData<List<ComponentData>>(emptyList())
    val components: LiveData<List<ComponentData>> = _components

    private val _selectedComponentId = MutableLiveData<Int?>(null)
    val selectedComponentId: LiveData<Int?> = _selectedComponentId

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    fun loadProject(projectId: String) {
        try {
            val proj = ProjectRepository.getProjectById(projectId)
            _project.value = proj
            _components.value = proj?.components ?: emptyList()
        } catch (e: Exception) {
            _errorMessage.value = "Load Project Failed: ${e.message}"
            CrashLogger.logError(getApplication(), "LoadProject", e)
        }
    }

    fun saveProject() {
        val currentProj = _project.value ?: return
        val currentComps = _components.value ?: emptyList()
        
        // Fix: content modification logic
        // currentComps might be the SAME list reference as currentProj.components if assigned directly during load.
        // To be safe, we create a copy first.
        val newComponentsList = ArrayList(currentComps)
        
        currentProj.components.clear()
        currentProj.components.addAll(newComponentsList)
        
        ProjectRepository.updateProject(currentProj)
    }

    fun addComponent(type: String, defaultTopic: String) {
        val currentList = _components.value?.toMutableList() ?: mutableListOf()
        val newComp = ComponentData(
            id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), // Temporary ID generation
            type = type,
            topicConfig = defaultTopic,
            x = 100f,
            y = 100f, // Default position
            width = if (type == "SWITCH") 200 else 300,
            height = if (type == "SWITCH") 100 else 150,
            label = type,
            props = mutableMapOf()
        )
        currentList.add(newComp)
        _components.value = currentList
        saveProject() // Auto-save on add
    }

    fun addComponent(component: ComponentData) {
        val currentList = _components.value?.toMutableList() ?: mutableListOf()
        currentList.add(component)
        _components.value = currentList
        saveProject()
    }

    fun removeComponent(componentId: Int) {
        val currentList = _components.value?.toMutableList() ?: return
        val removed = currentList.removeIf { it.id == componentId }
        if (removed) {
            _components.value = currentList
            if (_selectedComponentId.value == componentId) {
                _selectedComponentId.value = null
            }
            saveProject()
        }
    }

    fun updateComponent(updatedComponent: ComponentData) {
        val currentList = _components.value?.toMutableList() ?: return
        val index = currentList.indexOfFirst { it.id == updatedComponent.id }
        if (index != -1) {
            currentList[index] = updatedComponent
            _components.value = currentList
            saveProject()
        }
    }
    
    fun selectComponent(id: Int?) {
        _selectedComponentId.value = id
    }

    fun getSelectedComponent(): ComponentData? {
        val id = _selectedComponentId.value ?: return null
        return _components.value?.find { it.id == id }
    }
}
