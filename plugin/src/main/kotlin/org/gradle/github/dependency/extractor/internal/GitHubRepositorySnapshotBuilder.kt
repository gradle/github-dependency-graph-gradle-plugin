package org.gradle.github.dependency.extractor.internal

import com.github.packageurl.PackageURLBuilder
import org.gradle.github.dependency.extractor.internal.json.*
import org.gradle.github.dependency.extractor.internal.model.ResolvedComponent
import org.gradle.github.dependency.extractor.internal.model.ResolvedConfiguration
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

    fun addProject(identityPath: String, buildFileAbsolutePath: String) {
        val buildFilePath = Paths.get(buildFileAbsolutePath)
        projectToRelativeBuildFile[identityPath] = gitWorkspaceDirectory.relativize(buildFilePath).toString()
    }

    fun addResolvedConfiguration(configuration: ResolvedConfiguration) {
        val dependencyCollector = DependencyCollector(configuration.getRootComponent())
        for (component in configuration.components) {
            dependencyCollector.addDependency(component)
        }
        val name = "${configuration.rootId} [${configuration.name}]"
        addManifest(
            name = name,
            projectIdentityPath = configuration.identityPath,
            manifest = BaseGitHubManifest(
                name = name,
                resolved = dependencyCollector.resolved
            )
        )
    }

    private fun addManifest(
        name: String,
        projectIdentityPath: String,
        manifest: BaseGitHubManifest
    ) {
        bundledManifests[name] = BundledManifest(
            projectIdentityPath = projectIdentityPath,
            manifest = manifest
        )
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

    private class DependencyCollector(rootComponent: ResolvedComponent) {
        private val rootComponentId = rootComponent.id
        private val directDependencies = rootComponent.dependencies
        private val collected: MutableMap<String, GitHubDependency> = mutableMapOf()

        val resolved: Map<String, GitHubDependency> get() = collected

        fun addDependency(component: ResolvedComponent) {
            val name = component.id
            if (name == rootComponentId) {
                return
            }
            val existing = collected[name]
            if (existing?.relationship == GitHubDependency.Relationship.direct) {
                // Don't overwrite a direct dependency
                return
            }
            collected[name] = GitHubDependency(packageUrl(component), relationship(component), component.dependencies)
        }

        private fun relationship(component: ResolvedComponent) =
            if (directDependencies.contains(component.id)) GitHubDependency.Relationship.direct else GitHubDependency.Relationship.indirect

        private fun packageUrl(component: ResolvedComponent) =
            PackageURLBuilder
                .aPackageURL()
                .withType("maven")
                .withNamespace(component.coordinates.group.ifEmpty { component.coordinates.module }) // TODO: This is a sign of broken mapping from component -> PURL
                .withName(component.coordinates.module)
                .withVersion(component.coordinates.version)
                .also {
                    if (component.repositoryUrl != null) {
                        it.withQualifier("repository_url", component.repositoryUrl)
                    }
                }
                .build()
                .toString()
    }


    private data class BundledManifest(
        /**
         * Used to look up the file path of the build file in the [projectToRelativeBuildFile] map.
         */
        val projectIdentityPath: String,
        val manifest: BaseGitHubManifest
    )
}
