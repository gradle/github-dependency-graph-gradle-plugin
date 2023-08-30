package org.gradle.dependencygraph.simple

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.dependencygraph.AbstractDependencyExtractorPlugin
import org.gradle.forceresolve.ForceDependencyResolutionPlugin
import org.gradle.github.GitHubDependencyExtractorPlugin

@Suppress("unused")
class SimpleDependencyGraphPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        // Only apply the dependency extractor to the root build
        if (gradle.parent == null) {
            gradle.pluginManager.apply(SimpleDependencyExtractorPlugin::class.java)
        }

        // Apply the dependency resolver to each build
        gradle.pluginManager.apply(ForceDependencyResolutionPlugin::class.java)
    }

    class SimpleDependencyExtractorPlugin : AbstractDependencyExtractorPlugin() {
        override fun getRendererClassName(): String {
            return SimpleDependencyGraphRenderer::class.java.name
        }
    }
}