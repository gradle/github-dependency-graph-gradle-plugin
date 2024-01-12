package org.gradle.github

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.forceresolve.ForceDependencyResolutionPlugin
import org.gradle.util.GradleVersion

/**
 * A plugin that collects all resolved dependencies in a Gradle build for submission to the
 * GitHub Dependency Submission API.
 */
@Suppress("unused")
class GitHubDependencyGraphPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        val gradleVersion = GradleVersion.current().baseVersion
        if (gradleVersion < GradleVersion.version("5.2") ||
            (gradleVersion >= GradleVersion.version("7.0") && gradleVersion < GradleVersion.version("7.1"))) {
            throw GradleException("${this.javaClass.simpleName} is not supported for $gradleVersion.")
        }

        // Only apply the dependency extractor to the root build
        if (gradle.parent == null) {
            gradle.pluginManager.apply(GitHubDependencyExtractorPlugin::class.java)
        }

        // Apply the dependency resolver to each build
        gradle.pluginManager.apply(ForceDependencyResolutionPlugin::class.java)
    }
}
