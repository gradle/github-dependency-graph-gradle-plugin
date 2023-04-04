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

    /**
     * Map of all resolved configurations by name
     */
    private val resolvedConfigurations = mutableListOf<ResolvedConfiguration>()

    fun addProject(identityPath: String, buildFileAbsolutePath: String) {
        val buildFilePath = Paths.get(buildFileAbsolutePath)
        projectToRelativeBuildFile[identityPath] = gitWorkspaceDirectory.relativize(buildFilePath).toString()
    }

    fun addResolvedConfiguration(configuration: ResolvedConfiguration) {
        resolvedConfigurations.add(configuration)
    }

    fun build(): GitHubRepositorySnapshot {
        val manifestDependencies = mutableMapOf<String, DependencyCollector>()
        val manifestFiles = mutableMapOf<String, GitHubManifestFile?>()

        for (configuration in resolvedConfigurations) {
            val dependencyCollector = manifestDependencies.getOrPut(configuration.rootId) { DependencyCollector() }
            val rootComponent = configuration.getRootComponent()
            for (component in configuration.components) {
                dependencyCollector.addResolved(component, rootComponent)
            }

            manifestFiles.putIfAbsent(configuration.rootId, buildFileForProject(configuration.identityPath))
        }

        val manifests = manifestDependencies.mapValues { (name, collector) ->
            GitHubManifest(
                name,
                collector.resolved,
                manifestFiles[name]
            )
        }
        return GitHubRepositorySnapshot(
            job = job,
            sha = gitSha,
            ref = gitRef,
            detector = detector,
            manifests = manifests.toSortedMap()
        )
    }

    private fun buildFileForProject(identityPath: String): GitHubManifestFile? {
        return projectToRelativeBuildFile[identityPath]?.let {
            // Cleanup the path for Windows systems
            val sourceLocation = it.replace('\\', '/')
            GitHubManifestFile(sourceLocation = sourceLocation)
        }
    }

    private class DependencyCollector {
        val resolved: MutableMap<String, GitHubDependency> = mutableMapOf()

        /**
         * Merge each resolved component with the same ID into a single GitHubDependency.
         */
        fun addResolved(component: ResolvedComponent, rootComponent: ResolvedComponent) {
            if (component.id == rootComponent.id) {
                return
            }
            val relationship = relationship(rootComponent, component)
            val gitHubDependency = resolved.getOrPut(component.id) {
                GitHubDependency(
                    packageUrl(component),
                    relationship
                )
            }
            // Direct relationship trumps indirect
            if (relationship == GitHubDependency.Relationship.direct
                && gitHubDependency.relationship == GitHubDependency.Relationship.indirect) {
                gitHubDependency.relationship = GitHubDependency.Relationship.direct
            }
            // Add any new dependencies
            val existing = gitHubDependency.dependencies.toSet()
            for (dependency in component.dependencies) {
                if (!existing.contains(dependency)) {
                    gitHubDependency.dependencies.add(dependency)
                }
            }
        }

        private fun relationship(rootComponent: ResolvedComponent, component: ResolvedComponent) =
            if (rootComponent.dependencies.contains(component.id)) GitHubDependency.Relationship.direct else GitHubDependency.Relationship.indirect

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
}
