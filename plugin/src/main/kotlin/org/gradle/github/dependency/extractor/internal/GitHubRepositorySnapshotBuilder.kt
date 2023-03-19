package org.gradle.github.dependency.extractor.internal

import org.gradle.github.dependency.extractor.internal.json.*
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class GitHubRepositorySnapshotBuilder(
    private val gitHubJobName: String,
    private val gitHubRunNumber: String,
    private val gitSha: String,
    private val gitRef: String,
    private val gitWorkspaceDirectory: Path
) {

    private val detector by lazy { GitHubDetector() }

    private val job by lazy {
        GitHubJob(
            id = gitHubRunNumber,
            correlator = gitHubJobName
        )
    }

    /**
     * Map of the project identifier to the relative path of the git workspace directory [gitWorkspaceDirectory].
     */
    private val projectToRelativeBuildFile = ConcurrentHashMap<SimpleProjectIdentifier, String>()
    private val bundledManifests: MutableMap<String, BundledManifest> = ConcurrentHashMap()

    fun addManifest(
        name: String,
        buildPath: String,
        projectPath: String,
        manifest: BaseGitHubManifest
    ) {
        bundledManifests[name] = BundledManifest(
            projectIdentifier = SimpleProjectIdentifier(
                buildPath = buildPath,
                projectPath = projectPath
            ),
            manifest = manifest
        )
    }

    fun addProject(buildPath: String, projectPath: String, buildFileAbsolutePath: String) {
        val projectIdentifier = SimpleProjectIdentifier(buildPath, projectPath)
        val buildFilePath = Paths.get(buildFileAbsolutePath)
        projectToRelativeBuildFile[projectIdentifier] = gitWorkspaceDirectory.relativize(buildFilePath).toString()
    }

    fun build(): GitHubRepositorySnapshot {
        val manifests = bundledManifests.mapValues { (_, value) ->
            GitHubManifest(
                base = value.manifest,
                file = projectToRelativeBuildFile[value.projectIdentifier]?.let {
                    // Cleanup the path for Windows systems
                    val sourceLocation = it.replace('\\', '/')
                    GitHubManifestFile(sourceLocation = sourceLocation)
                }
            )
        }
        return GitHubRepositorySnapshot(
            job = job,
            sha = gitSha,
            ref = gitRef,
            detector = detector,
            manifests = manifests
        )
    }

    private data class BundledManifest(
        /**
         * Used to look up the file path of the build file in the [projectToRelativeBuildFile] map.
         */
        val projectIdentifier: SimpleProjectIdentifier,
        val manifest: BaseGitHubManifest
    )

    private data class SimpleProjectIdentifier(
        val buildPath: String,
        val projectPath: String
    )
}
