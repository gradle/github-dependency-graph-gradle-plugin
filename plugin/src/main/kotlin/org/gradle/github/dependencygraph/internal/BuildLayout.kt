package org.gradle.github.dependencygraph.internal

import org.gradle.github.dependencygraph.internal.json.GitHubManifestFile
import org.gradle.github.dependencygraph.internal.model.ResolvedConfiguration
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class BuildLayout(val gitWorkspaceDirectory: Path) {
    private val projectToRelativeBuildFile = ConcurrentHashMap<String, String>()

    fun addProject(identityPath: String, buildFileAbsolutePath: String) {
        val buildFilePath = Paths.get(buildFileAbsolutePath)
        projectToRelativeBuildFile[identityPath] = gitWorkspaceDirectory.relativize(buildFilePath).toString()
    }

    private fun buildFileForProject(identityPath: String): GitHubManifestFile? {
        return projectToRelativeBuildFile[identityPath]?.let {
            // Cleanup the path for Windows systems
            val sourceLocation = it.replace('\\', '/')
            GitHubManifestFile(sourceLocation = sourceLocation)
        }
    }

    fun getBuildFile(resolvedConfiguration: ResolvedConfiguration): GitHubManifestFile? {
        return buildFileForProject(resolvedConfiguration.identityPath)
    }
}