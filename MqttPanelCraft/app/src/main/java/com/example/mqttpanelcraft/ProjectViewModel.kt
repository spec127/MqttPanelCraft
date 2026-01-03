package com.example.mqttpanelcraft

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import java.util.UUID

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    // Repository is a Singleton Object, no instantiation needed
    // private val repository = ProjectRepository(application) 

    private val _currentProjectId = MutableLiveData<String>()
    
    // Reactive Project Data using MediatorLiveData to avoid Transformations dependency issues
    val project = androidx.lifecycle.MediatorLiveData<Project?>()
    
    // Components derived from Project
    val components = androidx.lifecycle.MediatorLiveData<List<ComponentData>>()

    init {
        // Update project whenever ID changes or Repository list changes
        val updateProjectFunc = {
            val id = _currentProjectId.value
            val list = ProjectRepository.projectsLiveData.value
            if (id != null && list != null) {
                project.value = list.find { it.id == id }
            } else {
                 // Optionally set null if not ready
            }
        }

        project.addSource(_currentProjectId) { updateProjectFunc() }
        project.addSource(ProjectRepository.projectsLiveData) { updateProjectFunc() }
        
        // Update components whenever project changes
        components.addSource(project) { proj ->
            components.value = proj?.components ?: emptyList() 
        }
    }

    private val _selectedComponentId = MutableLiveData<Int?>(null)
    val selectedComponentId: LiveData<Int?> = _selectedComponentId

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    fun loadProject(projectId: String) {
        _currentProjectId.value = projectId
    }

    fun saveProject() {
        val currentProj = project.value ?: return
        // Note: ProjectRepository.updateProject needs the EXACT object or ID matching.
        // Since we are observing the live list, 'currentProj' is a reference to the object in the list 
        // (or a copy depending on Repository impl). 
        // Repository uses CopyOnWriteList, so we should be careful.
        // Actually Repository.updateProject replaces by ID.
        // The 'components' LiveData is derived. If we modify 'currentProj.components' directly,
        // we should call updateProject to notify others and save to disk.
        
        ProjectRepository.updateProject(currentProj)
    }

    fun addComponent(type: String, defaultTopic: String) {
        val proj = project.value ?: return
        val newComp = ComponentData(
            id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), 
            type = type,
            topicConfig = defaultTopic,
            x = 100f,
            y = 100f, 
            width = if (type == "SWITCH") 200 else 300,
            height = if (type == "SWITCH") 100 else 150,
            label = type,
            props = mutableMapOf()
        )
        proj.components.add(newComp)
        saveProject() // Triggers Repository update -> LiveData update
    }

    fun addComponent(component: ComponentData) {
        val proj = project.value ?: return
        proj.components.add(component)
        saveProject()
    }

    fun removeComponent(componentId: Int) {
        val proj = project.value ?: return
        val removed = proj.components.removeIf { it.id == componentId }
        if (removed) {
            if (_selectedComponentId.value == componentId) {
                _selectedComponentId.value = null
            }
            saveProject()
        }
    }

    fun updateComponent(updatedComponent: ComponentData) {
        val proj = project.value ?: return
        val index = proj.components.indexOfFirst { it.id == updatedComponent.id }
        if (index != -1) {
            proj.components[index] = updatedComponent
            saveProject()
        }
    }
    
    fun selectComponent(id: Int?) {
        _selectedComponentId.value = id
    }

    fun getSelectedComponent(): ComponentData? {
        val id = _selectedComponentId.value ?: return null
        return components.value?.find { it.id == id }
    }
    fun updateComponentsBatch(updatedComponents: List<ComponentData>) {
        val proj = project.value ?: return
        var changed = false
        updatedComponents.forEach { updated ->
            val index = proj.components.indexOfFirst { it.id == updated.id }
            if (index != -1) {
                proj.components[index] = updated
                changed = true
            }
        }
        if (changed) saveProject()
    }
}
