package org.gradle.github.dependency.extractor.internal.model

data class ResolvedConfiguration(val rootId: String, val identityPath: String, val name: String, val components: MutableList<ResolvedComponent> = mutableListOf()) {
    fun hasComponent(componentId: String): Boolean {
        return components.map { it.id }.contains(componentId)
    }

    fun getRootComponent(): ResolvedComponent {
        return components.find { it.id == rootId }!!
    }
}
