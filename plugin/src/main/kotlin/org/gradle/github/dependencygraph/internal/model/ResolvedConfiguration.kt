package org.gradle.github.dependencygraph.internal.model

data class ResolvedConfiguration(val id: String,
                                 val identityPath: String,
                                 val directDependencies: MutableList<ResolvedComponent> = mutableListOf(),
                                 val allDependencies: MutableList<ResolvedComponent> = mutableListOf()) {
    fun addDirectDependency(component: ResolvedComponent) {
        directDependencies.add(component)
        allDependencies.add(component)
    }

    fun addDependency(component: ResolvedComponent) {
        allDependencies.add(component)
    }

    fun hasDependency(componentId: String): Boolean {
        return allDependencies.map { it.id }.contains(componentId)
    }
}
