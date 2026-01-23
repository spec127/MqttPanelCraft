package com.example.mqttpanelcraft.ui.components

import com.example.mqttpanelcraft.ui.components.definitions.ButtonDefinition
// Import other definitions as created

object ComponentDefinitionRegistry {
    
    private val definitions = mutableMapOf<String, IComponentDefinition>()

    init {
        // We will register them here
        register(ButtonDefinition)
        register(com.example.mqttpanelcraft.ui.components.definitions.LedDefinition)
        register(com.example.mqttpanelcraft.ui.components.definitions.TextDefinition)
        register(com.example.mqttpanelcraft.ui.components.definitions.SliderDefinition)
        register(com.example.mqttpanelcraft.ui.components.definitions.ImageDefinition)
        register(com.example.mqttpanelcraft.ui.components.definitions.CameraDefinition)
        register(com.example.mqttpanelcraft.ui.components.definitions.ThermometerDefinition)
    }

    fun register(def: IComponentDefinition) {
        definitions[def.type] = def
    }

    fun get(type: String): IComponentDefinition? {
        return definitions[type]
    }

    fun getAllTypes(): Set<String> = definitions.keys
}
