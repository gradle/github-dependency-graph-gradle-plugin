package org.gradle.github.dependencygraph

import org.gradle.api.logging.Logging
import org.gradle.dependencygraph.DependencyGraphRenderer
import org.gradle.github.dependencygraph.model.GitHubRepositorySnapshot
import org.gradle.dependencygraph.model.BuildLayout
import org.gradle.dependencygraph.model.ResolvedConfiguration
import org.gradle.dependencygraph.util.*
import java.io.File

class GitHubDependencyGraphRenderer() : DependencyGraphRenderer {

    override fun outputDependencyGraph(
        pluginParameters: PluginParameters,
        buildLayout: BuildLayout,
        resolvedConfigurations: MutableList<ResolvedConfiguration>,
        outputDirectory: File
    ) {
        val snapshotParams = GitHubSnapshotParams(pluginParameters)
        val gitHubRepositorySnapshotBuilder = GitHubRepositorySnapshotBuilder(snapshotParams)
        // Use the job correlator as the manifest name
        val manifestName = snapshotParams.dependencyGraphJobCorrelator
        val manifest = gitHubRepositorySnapshotBuilder.buildManifest(manifestName, resolvedConfigurations, buildLayout)
        val snapshot = gitHubRepositorySnapshotBuilder.buildSnapshot(manifest)

        val outputFile = File(outputDirectory, "${snapshotParams.dependencyGraphJobCorrelator}.json")

        writeDependencySnapshot(snapshot, outputFile)
    }

    private fun writeDependencySnapshot(graph: GitHubRepositorySnapshot, manifestFile: File) {
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(JacksonJsonSerializer.serializeToJson(graph))
        LOGGER.lifecycle("\nGitHubDependencyGraphRenderer: Wrote dependency snapshot to \n${manifestFile.canonicalPath}")
    }

    companion object {
        private val LOGGER = Logging.getLogger(GitHubDependencyGraphRenderer::class.java)
    }
}