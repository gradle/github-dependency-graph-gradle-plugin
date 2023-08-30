package org.gradle.github

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.forceresolve.ForceDependencyResolutionPlugin

/**
 * A plugin that collects all resolved dependencies in a Gradle build for submission to the
 * GitHub Dependency Submission API.
 */
@Suppress("unused")
class GitHubDependencyGraphPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        // Only apply the dependency extractor to the root build
        if (gradle.parent == null) {
            gradle.pluginManager.apply(GitHubDependencyExtractorPlugin::class.java)
        }

        // Apply the dependency resolver to each build
        gradle.pluginManager.apply(ForceDependencyResolutionPlugin::class.java)
    }
}
