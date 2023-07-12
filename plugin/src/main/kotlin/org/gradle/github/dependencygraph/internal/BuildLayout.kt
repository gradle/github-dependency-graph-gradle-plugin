package org.gradle.github.dependencygraph.internal

import org.gradle.github.dependencygraph.internal.json.GitHubManifestFile
import org.gradle.github.dependencygraph.internal.model.DependencySource
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class BuildLayout(val gitWorkspaceDirectory: Path) {
    private val buildPathToSettingsFile = ConcurrentHashMap<String, String>()
    private val projectPathToBuildFile = ConcurrentHashMap<String, String>()

    fun addSettings(identityPath: String, settingsFilePath: String) {
        buildPathToSettingsFile[identityPath] = gitWorkspaceDirectory.relativize(Paths.get(settingsFilePath)).toString()
    }

    fun addProject(identityPath: String, buildFileAbsolutePath: String) {
        val buildFilePath = Paths.get(buildFileAbsolutePath)
        projectPathToBuildFile[identityPath] = gitWorkspaceDirectory.relativize(buildFilePath).toString()
    }

    fun getBuildFile(dependencySource: DependencySource): GitHubManifestFile? {
        return getSourcePath(dependencySource)?.let {
            // Cleanup the path for Windows systems
            val sourceLocation = it.replace('\\', '/')
            GitHubManifestFile(sourceLocation = sourceLocation)
        }
    }

    private fun getSourcePath(dependencySource: DependencySource): String? {
        if (isProject(dependencySource)) {
            return projectPathToBuildFile[dependencySource.path]
        }
        return buildPathToSettingsFile[dependencySource.path]
    }

    // TODO:DAZ Model this better
    private fun isProject(dependencySource: DependencySource): Boolean {
        return dependencySource.id.startsWith("project ")
    }
}