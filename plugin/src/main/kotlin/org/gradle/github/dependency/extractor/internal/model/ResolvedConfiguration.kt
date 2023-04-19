package org.gradle.github.dependency.extractor.internal.model

data class ResolvedConfiguration(val buildPath: String, val identityPath: String?, val configName: String, val components: MutableList<ResolvedComponent> = mutableListOf()) {
    fun hasComponent(componentId: String): Boolean {
        return components.map { it.id }.contains(componentId)
    }

    fun getRootComponent(): ResolvedComponent {
        return components.first()
    }
}
