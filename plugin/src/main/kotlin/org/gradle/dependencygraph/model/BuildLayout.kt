package org.gradle.dependencygraph.model

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class BuildLayout {
    private val buildPathToSettingsFile = ConcurrentHashMap<String, String>()
    private val projectPathToBuildFile = ConcurrentHashMap<String, String>()

    fun addSettings(identityPath: String, settingsFilePath: String) {
        buildPathToSettingsFile[identityPath] = settingsFilePath
    }

    fun addProject(identityPath: String, buildFileAbsolutePath: String) {
        projectPathToBuildFile[identityPath] = buildFileAbsolutePath
    }

    /**
     * Returns the absolute path to the root build settings file if it exists, or the root build file if not.
     */
    fun getRootBuildPath(): Path? {
        return getRootBuildAbsoluteFile()?.let { Paths.get(it) }
    }

    private fun getRootBuildAbsoluteFile(): String? {
        val rootPath = ":"
        val settingsFile = buildPathToSettingsFile[rootPath]
        if (settingsFile != null && File(settingsFile).exists()) {
            return settingsFile
        }
        return projectPathToBuildFile[rootPath]
    }
}