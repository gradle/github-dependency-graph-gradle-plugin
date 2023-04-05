package org.gradle.github.dependency

import org.gradle.api.Plugin
import org.gradle.api.internal.GradleInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logging
import org.gradle.github.dependency.extractor.ForceDependencyResolutionPlugin
import org.gradle.github.dependency.extractor.GitHubDependencyExtractorPlugin

@Suppress("unused")
class GitHubDependencySubmissionPlugin : Plugin<Gradle> {

    private val LOGGER = Logging.getLogger(GitHubDependencySubmissionPlugin::class.java)

    override fun apply(gradle: Gradle) {
        LOGGER.lifecycle("Applying Plugin: GitHubDependencySubmissionPlugin")
        val buildPath = (gradle as GradleInternal).identityPath
        // Only apply the dependency extractor to the root build
        if (gradle.parent == null) {
            LOGGER.info("Applying Plugin: GitHubDependencyExtractorPlugin to root build")
            gradle.pluginManager.apply(GitHubDependencyExtractorPlugin::class.java)
        }

        // Apply the dependency resolver to all builds
        LOGGER.info("Applying Plugin: ForceDependencyResolutionPlugin to build $buildPath")
        gradle.pluginManager.apply(ForceDependencyResolutionPlugin::class.java)
    }
}
