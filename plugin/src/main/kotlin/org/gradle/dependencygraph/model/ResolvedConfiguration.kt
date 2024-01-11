package org.gradle.dependencygraph.model

data class ResolvedConfiguration(
    val rootOrigin: DependencyOrigin,
    val configurationName: String,
    val allDependencies: MutableList<ResolvedDependency> = mutableListOf()
) {
    fun addDependency(component: ResolvedDependency) {
        allDependencies.add(component)
    }

    fun hasDependency(componentId: String): Boolean {
        return allDependencies.map { it.id }.contains(componentId)
    }
}
