package org.gradle.github.dependencygraph.internal.model

data class ResolvedConfiguration(
    val rootSource: DependencySource,
    val allDependencies: MutableList<ResolvedDependency> = mutableListOf()
) {
    fun addDependency(component: ResolvedDependency) {
        allDependencies.add(component)
    }

    fun hasDependency(componentId: String): Boolean {
        return allDependencies.map { it.id }.contains(componentId)
    }
}
