package org.gradle.github.dependencygraph.internal.github

import org.gradle.github.dependencygraph.internal.model.BuildLayout
import org.gradle.github.dependencygraph.internal.model.ResolvedConfiguration
import java.io.File

class GitHubDependencyGraphOutput(val snapshotParams: GitHubSnapshotParams, val outputDir: File) {

    fun outputDependencyGraph(resolvedConfigurations: MutableList<ResolvedConfiguration>, buildLayout: BuildLayout) {
        val outputFile = File(outputDir, "${snapshotParams.dependencyGraphJobCorrelator}.json")
        val fileWriter = DependencyFileWriter(outputFile)

        val gitHubRepositorySnapshotBuilder = GitHubRepositorySnapshotBuilder(snapshotParams)

        val snapshot = gitHubRepositorySnapshotBuilder.build(resolvedConfigurations, buildLayout)
        fileWriter.writeDependencySnapshot(snapshot)
    }
}