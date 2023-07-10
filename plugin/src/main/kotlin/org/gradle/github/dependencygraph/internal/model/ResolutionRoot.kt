package org.gradle.github.dependencygraph.internal.model

/**
 * A root component in dependency resolution, this represents a component that "owns" or declares
 * dependencies within a given resolution.
 * In most cases, this will be the project component that declares the dependency, but for certain resolutions
 * this component will represent an overall build.
 */
data class ResolutionRoot(val id: String, val path: String)
