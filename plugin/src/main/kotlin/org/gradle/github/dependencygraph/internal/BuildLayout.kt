package org.gradle.github.dependencygraph.internal

import org.gradle.github.dependencygraph.internal.json.GitHubManifestFile
import org.gradle.github.dependencygraph.internal.model.DependencySource
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class BuildLayout(val gitWorkspaceDirectory: Path) {
    private val projectToRelativeBuildFile = ConcurrentHashMap<String, String>()

    fun addProject(identityPath: String, buildFileAbsolutePath: String) {
        val buildFilePath = Paths.get(buildFileAbsolutePath)
        projectToRelativeBuildFile[identityPath] = gitWorkspaceDirectory.relativize(buildFilePath).toString()
    }

    fun getBuildFile(dependencySource: DependencySource): GitHubManifestFile? {
        return projectToRelativeBuildFile[dependencySource.path]?.let {
            // Cleanup the path for Windows systems
            val sourceLocation = it.replace('\\', '/')
            GitHubManifestFile(sourceLocation = sourceLocation)
        }
    }
}