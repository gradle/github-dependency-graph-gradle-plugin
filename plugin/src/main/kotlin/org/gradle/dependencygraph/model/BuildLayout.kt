package org.gradle.dependencygraph.model

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class BuildLayout(private val gitWorkspaceDirectory: Path) {
    private val buildPathToSettingsFile = ConcurrentHashMap<String, String>()
    private val projectPathToBuildFile = ConcurrentHashMap<String, String>()

    fun addSettings(identityPath: String, settingsFilePath: String) {
        buildPathToSettingsFile[identityPath] = settingsFilePath
    }

    fun addProject(identityPath: String, buildFileAbsolutePath: String) {
        projectPathToBuildFile[identityPath] = buildFileAbsolutePath
    }

    /**
     * Returns the relative path to the root build settings file if it exists, or the root build file if not.
     */
    fun getRootBuildFileRelativePath(): String? {
        val filePath = getManifestFileRelativePath(":")
        // Clean up path for Windows systems
        return filePath?.toString()?.replace('\\', '/')
    }

    private fun getManifestFileRelativePath(identityPath: String): Path? {
        val settingsFile = buildPathToSettingsFile[identityPath]
        if (settingsFile != null && File(settingsFile).exists()) {
            return toRelativePath(settingsFile)
        }
        val buildFile = projectPathToBuildFile[identityPath]
        return toRelativePath(buildFile)
    }

    private fun toRelativePath(fileAbsolutePath: String?): Path? {
        return fileAbsolutePath?.let {
            gitWorkspaceDirectory.relativize(Paths.get(fileAbsolutePath))
        }
    }
}