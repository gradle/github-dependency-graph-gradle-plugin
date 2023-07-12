package org.gradle.github.dependencygraph.internal.model

data class ResolvedDependency(
    val id: String,
    val source: DependencySource,
    val direct: Boolean,
    val coordinates: DependencyCoordinates,
    val repositoryUrl: String?,
    val dependencies: List<String>
)
