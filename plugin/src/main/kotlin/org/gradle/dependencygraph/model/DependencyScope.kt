package org.gradle.dependencygraph.model

/**
 * Represents the scope of a resolved dependency.
 * At this point, the scopes are limited to those exposed in the GitHub DependencySubmission API.
 * Later development may extend this to a richer set of scopes.
 */
enum class DependencyScope {
    DEVELOPMENT, RUNTIME;

    companion object {
        fun getEffectiveScope(scopes: List<DependencyScope>): DependencyScope {
            if (scopes.contains(RUNTIME)) return RUNTIME
            return DEVELOPMENT
        }
    }
}