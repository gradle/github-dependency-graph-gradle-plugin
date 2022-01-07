package org.gradle.github.dependency.extractor.internal

import org.gradle.github.dependency.extractor.internal.json.BaseGitHubManifest
import org.gradle.github.dependency.extractor.internal.json.GitHubDependencyGraph
import org.gradle.github.dependency.extractor.internal.json.GitHubManifest
import org.gradle.github.dependency.extractor.internal.json.GitHubManifestFile
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class GitHubDependencyGraphBuilder(
    private val gitWorkspaceDirectory: Path
) {

    private val projectToBuildPath = ConcurrentHashMap<SimpleProjectIdentifier, String>()
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
        projectToBuildPath[projectIdentifier] = gitWorkspaceDirectory.relativize(buildFilePath).toString()
    }

    fun build(): GitHubDependencyGraph {
        val manifests = bundledManifests.mapValues { (_, value) ->
            GitHubManifest(
                base = value.manifest,
                file = projectToBuildPath[value.projectIdentifier]?.let {
                    GitHubManifestFile(sourceLocation = it)
                }
            )
        }
        return GitHubDependencyGraph(
            manifests = manifests
        )
    }

    private data class BundledManifest(
        val projectIdentifier: SimpleProjectIdentifier,
        val manifest: BaseGitHubManifest
    )

    private data class SimpleProjectIdentifier(
        val buildPath: String,
        val projectPath: String
    )
}
