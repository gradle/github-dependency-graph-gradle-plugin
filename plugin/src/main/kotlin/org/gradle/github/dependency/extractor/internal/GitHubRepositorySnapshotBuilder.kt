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
    private val projectToRelativeBuildFile = ConcurrentHashMap<String, String>()
    private val bundledManifests: MutableMap<String, BundledManifest> = ConcurrentHashMap()

    fun addManifest(
        name: String,
        projectIdentityPath: String,
        manifest: BaseGitHubManifest
    ) {
        bundledManifests[name] = BundledManifest(
            projectIdentityPath = projectIdentityPath,
            manifest = manifest
        )
    }

    fun addProject(identityPath: String, buildFileAbsolutePath: String) {
        val buildFilePath = Paths.get(buildFileAbsolutePath)
        projectToRelativeBuildFile[identityPath] = gitWorkspaceDirectory.relativize(buildFilePath).toString()
    }

    fun build(): GitHubRepositorySnapshot {
        val manifests = bundledManifests.mapValues { (_, value) ->
            GitHubManifest(
                base = value.manifest,
                file = projectToRelativeBuildFile[value.projectIdentityPath]?.let {
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
        val projectIdentityPath: String,
        val manifest: BaseGitHubManifest
    )
}
