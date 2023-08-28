package org.gradle.github.dependencygraph

import org.gradle.github.dependencygraph.internal.github.GitHubDependencyGraphRenderer

/**
 * A plugin that collects all resolved dependencies in a Gradle build and exports it using the GitHub API format.
 */
class GitHubDependencyExtractorPlugin : AbstractDependencyExtractorPlugin() {
    override fun getRendererClassName(): String {
        return GitHubDependencyGraphRenderer::class.java.name
    }
}
