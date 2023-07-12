package org.gradle.github.dependencygraph.internal

import com.github.packageurl.PackageURLBuilder
import org.gradle.github.dependencygraph.internal.model.ResolvedDependency
import org.gradle.github.dependencygraph.internal.model.ResolvedConfiguration
import org.gradle.github.dependencygraph.internal.json.*
import org.gradle.github.dependencygraph.internal.model.DependencySource

private const val DEFAULT_MAVEN_REPOSITORY_URL = "https://repo.maven.apache.org/maven2"

class GitHubRepositorySnapshotBuilder(
    private val dependencyGraphJobCorrelator: String,
    private val dependencyGraphJobId: String,
    private val gitSha: String,
    private val gitRef: String
) {

    private val detector by lazy { GitHubDetector() }

    private val job by lazy {
        GitHubJob(
            id = dependencyGraphJobId,
            correlator = dependencyGraphJobCorrelator
        )
    }

    fun build(resolvedConfigurations: MutableList<ResolvedConfiguration>, buildLayout: BuildLayout): GitHubRepositorySnapshot {
        val manifestDependencies = mutableMapOf<String, DependencyCollector>()

        for (resolutionRoot in resolvedConfigurations) {
            for (dependency in resolutionRoot.allDependencies) {
                // Ignore project dependencies (transitive deps of projects will be reported with project)
                if (isProject(dependency)) continue

                val source = dependency.source
                val dependencyCollector = manifestDependencies.getOrPut(source.id) { DependencyCollector(source) }
                dependencyCollector.addResolved(dependency)
            }
        }

        val manifests = manifestDependencies.mapValues { (name, collector) ->
            val manifestFile = buildLayout.getBuildFile(collector.dependencySource)

            GitHubManifest(
                name,
                collector.getDependencies(),
                manifestFile
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

    // TODO:DAZ Model this better
    private fun isProject(dependency: ResolvedDependency): Boolean {
        return dependency.id.startsWith("project ")
    }

    private class DependencyCollector(val dependencySource: DependencySource) {
        private val dependencyBuilders: MutableMap<String, GitHubDependencyBuilder> = mutableMapOf()

        /**
         * Merge each resolved component with the same ID into a single GitHubDependency.
         */
        fun addResolved(component: ResolvedDependency) {
            val dep = dependencyBuilders.getOrPut(component.id) {
                GitHubDependencyBuilder(packageUrl(component))
            }
            dep.addRelationship(relationship(component))
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

        private fun relationship(component: ResolvedDependency) =
            if (component.direct) GitHubDependency.Relationship.direct else GitHubDependency.Relationship.indirect

        private fun packageUrl(component: ResolvedDependency) =
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
