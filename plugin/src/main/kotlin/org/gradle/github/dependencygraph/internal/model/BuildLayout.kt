package org.gradle.github.dependencygraph.internal.model

import org.gradle.github.dependencygraph.internal.github.json.GitHubManifestFile
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class BuildLayout(private val gitWorkspaceDirectory: Path) {
    private val projectToRelativeBuildFile = ConcurrentHashMap<String, String>()

    fun addProject(identityPath: String, buildFileAbsolutePath: String) {
        val buildFilePath = Paths.get(buildFileAbsolutePath)
        projectToRelativeBuildFile[identityPath] = gitWorkspaceDirectory.relativize(buildFilePath).toString()
    }

    fun getBuildFile(identityPath: String): GitHubManifestFile? {
        return projectToRelativeBuildFile[identityPath]?.let {
            // Cleanup the path for Windows systems
            val sourceLocation = it.replace('\\', '/')
            GitHubManifestFile(sourceLocation = sourceLocation)
        }
    }
}