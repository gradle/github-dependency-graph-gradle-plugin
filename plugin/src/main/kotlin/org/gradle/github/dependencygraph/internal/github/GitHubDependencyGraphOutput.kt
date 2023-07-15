package org.gradle.github.dependencygraph.internal.github

import org.gradle.api.logging.Logging
import org.gradle.github.dependencygraph.internal.github.json.GitHubRepositorySnapshot
import org.gradle.github.dependencygraph.internal.model.BuildLayout
import org.gradle.github.dependencygraph.internal.model.ResolvedConfiguration
import java.io.File

class GitHubDependencyGraphOutput(private val snapshotParams: GitHubSnapshotParams, private val outputDir: File) {

    fun outputDependencyGraph(resolvedConfigurations: MutableList<ResolvedConfiguration>, buildLayout: BuildLayout) {
        val gitHubRepositorySnapshotBuilder = GitHubRepositorySnapshotBuilder(snapshotParams)
        // Use the job correlator as the manifest name
        val manifestName = snapshotParams.dependencyGraphJobCorrelator
        val manifest = gitHubRepositorySnapshotBuilder.buildManifest(manifestName, resolvedConfigurations, buildLayout)
        val snapshot = gitHubRepositorySnapshotBuilder.buildSnapshot(manifest)

        val outputFile = File(outputDir, "${snapshotParams.dependencyGraphJobCorrelator}.json")

        writeDependencySnapshot(snapshot, outputFile)
    }

    private fun writeDependencySnapshot(graph: GitHubRepositorySnapshot, manifestFile: File) {
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(JacksonJsonSerializer.serializeToJson(graph))
        LOGGER.lifecycle("\nGitHubDependencyGraphPlugin: Wrote dependency snapshot to \n${manifestFile.canonicalPath}")
    }

    companion object {
        private val LOGGER = Logging.getLogger(GitHubDependencyGraphOutput::class.java)
    }
}