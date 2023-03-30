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
    private val resolvedConfigurations: MutableMap<String, ResolvedConfiguration> = ConcurrentHashMap()

    fun addProject(identityPath: String, buildFileAbsolutePath: String) {
        val buildFilePath = Paths.get(buildFileAbsolutePath)
        projectToRelativeBuildFile[identityPath] = gitWorkspaceDirectory.relativize(buildFilePath).toString()
    }

    fun addResolvedConfiguration(configuration: ResolvedConfiguration) {
        val name = "${configuration.rootId} [${configuration.name}]"
        resolvedConfigurations[name] = configuration
    }

    fun build(): GitHubRepositorySnapshot {
        val manifests = resolvedConfigurations.mapValues { (name, configuration) ->
            val dependencyCollector = DependencyCollector(configuration.getRootComponent())
            for (component in configuration.components) {
                dependencyCollector.addDependency(component)
            }
            GitHubManifest(
                name = name,
                resolved = dependencyCollector.resolved,
                file = buildFileForProject(configuration)
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

    private fun buildFileForProject(configuration: ResolvedConfiguration): GitHubManifestFile? {
        val file = projectToRelativeBuildFile[configuration.identityPath]?.let {
            // Cleanup the path for Windows systems
            val sourceLocation = it.replace('\\', '/')
            GitHubManifestFile(sourceLocation = sourceLocation)
        }
        return file
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
}
