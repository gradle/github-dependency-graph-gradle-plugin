package org.gradle.github.dependencygraph.internal

import com.github.packageurl.PackageURLBuilder
import org.gradle.github.dependencygraph.internal.model.ResolvedComponent
import org.gradle.github.dependencygraph.internal.model.ResolvedConfiguration
import org.gradle.github.dependencygraph.internal.json.*
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

private const val DEFAULT_MAVEN_REPOSITORY_URL = "https://repo.maven.apache.org/maven2"

class GitHubRepositorySnapshotBuilder(
    private val dependencyGraphJobCorrelator: String,
    private val dependencyGraphJobId: String,
    private val gitSha: String,
    private val gitRef: String,
    private val gitWorkspaceDirectory: Path
) {

    private val detector by lazy { GitHubDetector() }

    private val job by lazy {
        GitHubJob(
            id = dependencyGraphJobId,
            correlator = dependencyGraphJobCorrelator
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
            val manifestName = manifestName(configuration)
            val dependencyCollector = manifestDependencies.getOrPut(manifestName) { DependencyCollector() }
            val rootComponent = configuration.getRootComponent()
            for (component in configuration.components) {
                dependencyCollector.addResolved(component, rootComponent)
            }

            // If not assigned to a project, assume the root project for the assigned build.
            manifestFiles.putIfAbsent(manifestName, buildFileForProject(configuration.identityPath ?: configuration.buildPath))
        }

        val manifests = manifestDependencies.mapValues { (name, collector) ->
            GitHubManifest(
                name,
                collector.getDependencies(),
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

    private fun manifestName(config: ResolvedConfiguration): String {
        if (config.identityPath != null) {
            return "project ${config.identityPath}"
        }
        return "build ${config.buildPath}"
    }

    private fun buildFileForProject(identityPath: String): GitHubManifestFile? {
        return projectToRelativeBuildFile[identityPath]?.let {
            // Cleanup the path for Windows systems
            val sourceLocation = it.replace('\\', '/')
            GitHubManifestFile(sourceLocation = sourceLocation)
        }
    }

    private class DependencyCollector {
        private val dependencyBuilders: MutableMap<String, GitHubDependencyBuilder> = mutableMapOf()

        /**
         * Merge each resolved component with the same ID into a single GitHubDependency.
         */
        fun addResolved(component: ResolvedComponent, rootComponent: ResolvedComponent) {
            if (component.id == rootComponent.id) {
                return
            }
            val dep = dependencyBuilders.getOrPut(component.id) {
                GitHubDependencyBuilder(packageUrl(component))
            }
            dep.addRelationship(relationship(rootComponent, component))
            dep.addDependencies(component.dependencies)
        }

        /**
         * Build the GitHubDependency instances
         */
        fun getDependencies(): Map<String, GitHubDependency> {
            return dependencyBuilders.mapValues { (_, builder) ->
                builder.build()
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
                    if (component.repositoryUrl != null && component.repositoryUrl != DEFAULT_MAVEN_REPOSITORY_URL) {
                        it.withQualifier("repository_url", component.repositoryUrl)
                    }
                }
                .build()
                .toString()

        private class GitHubDependencyBuilder(val package_url: String) {
            var relationship: GitHubDependency.Relationship = GitHubDependency.Relationship.indirect
            val dependencies = mutableListOf<String>()

            fun addRelationship(newRelationship: GitHubDependency.Relationship) {
                // Direct relationship trumps indirect
                if (relationship == GitHubDependency.Relationship.indirect) {
                    relationship = newRelationship
                }
            }

            fun addDependencies(newDependencies: List<String>) {
                // Add any dependencies that are not in the existing set
                for (newDependency in newDependencies.subtract(dependencies.toSet())) {
                    dependencies.add(newDependency)
                }
            }

            fun build(): GitHubDependency {
                return GitHubDependency(package_url, relationship, dependencies)
            }
        }
    }
}
