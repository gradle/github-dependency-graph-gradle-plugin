package org.gradle.github.dependency.extractor.internal.model

data class ResolvedComponent(val id: String, val coordinates: ComponentCoordinates, val repositoryUrl: String?, val dependencies: List<String>)
