package org.gradle.github.dependencygraph.internal.model

data class ResolvedConfiguration(val id: String,
                                 val identityPath: String,
                                 val allDependencies: MutableList<ResolvedComponent> = mutableListOf()) {
    fun addDependency(component: ResolvedComponent) {
        allDependencies.add(component)
    }

    fun hasDependency(componentId: String): Boolean {
        return allDependencies.map { it.id }.contains(componentId)
    }
}
