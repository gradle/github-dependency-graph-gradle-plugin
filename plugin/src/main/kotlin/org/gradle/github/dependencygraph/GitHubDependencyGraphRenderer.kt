package org.gradle.github.dependencygraph

import org.gradle.api.logging.Logging
import org.gradle.dependencygraph.DependencyGraphRenderer
import org.gradle.github.dependencygraph.model.GitHubRepositorySnapshot
import org.gradle.dependencygraph.model.BuildLayout
import org.gradle.dependencygraph.model.ResolvedConfiguration
import org.gradle.dependencygraph.util.*
import java.io.File

class GitHubDependencyGraphRenderer : DependencyGraphRenderer {

    override fun outputDependencyGraph(
        pluginParameters: PluginParameters,
        buildLayout: BuildLayout,
        resolvedConfigurations: List<ResolvedConfiguration>,
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

        // Write the output file as a GitHub Actions step output
        val githubOutput = System.getenv("GITHUB_OUTPUT")
        if (githubOutput !== null && File(githubOutput).isFile) {
            File(githubOutput).appendText("dependency-graph-file=${outputFile.absolutePath}\n")
        }
    }

    private fun writeDependencySnapshot(graph: GitHubRepositorySnapshot, manifestFile: File) {
        manifestFile.writeText(JacksonJsonSerializer.serializeToJson(graph))
        LOGGER.lifecycle("\nGitHubDependencyGraphRenderer: Wrote dependency snapshot to \n${manifestFile.canonicalPath}")
    }

    companion object {
        private val LOGGER = Logging.getLogger(GitHubDependencyGraphRenderer::class.java)
    }
}