package org.gradle.github

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logging
import org.gradle.github.dependencygraph.ForceDependencyResolutionPlugin
import org.gradle.github.dependencygraph.GitHubDependencyExtractorPlugin
import org.gradle.github.dependencygraph.internal.util.PluginCompanionUtils

/**
 * A plugin that collects all resolved dependencies in a Gradle build for submission to the
 * GitHub Dependency Submission API.
 */
@Suppress("unused")
class GitHubDependencyGraphPlugin : Plugin<Gradle> {
    private companion object : PluginCompanionUtils() {
        private val LOGGER = Logging.getLogger(GitHubDependencyGraphPlugin::class.java)
    }

    override fun apply(gradle: Gradle) {
        // Only apply the dependency extractor to the root build
        if (gradle.parent == null) {
            gradle.pluginManager.apply(GitHubDependencyExtractorPlugin::class.java)
        }

        // Apply the dependency resolver to each build
        gradle.pluginManager.apply(ForceDependencyResolutionPlugin::class.java)
    }
}
