package org.gradle.github.dependencygraph.internal.github

import org.gradle.github.dependencygraph.internal.github.json.*
import org.gradle.github.dependencygraph.internal.model.ResolvedDependency
import org.gradle.github.dependencygraph.internal.model.ResolvedConfiguration
import org.gradle.github.dependencygraph.internal.model.BuildLayout
import org.gradle.github.dependencygraph.internal.model.DependencySource

class GitHubRepositorySnapshotBuilder(
    private val snapshotParams: GitHubSnapshotParams
) {

    private val detector by lazy { GitHubDetector() }

    private val job by lazy {
        GitHubJob(
            id = snapshotParams.dependencyGraphJobId,
            correlator = snapshotParams.dependencyGraphJobCorrelator
        )
    }

    fun build(resolvedConfigurations: MutableList<ResolvedConfiguration>, buildLayout: BuildLayout): GitHubRepositorySnapshot {
        val manifestDependencies = mutableMapOf<String, DependencyCollector>()

        for (resolutionRoot in resolvedConfigurations) {
            for (dependency in resolutionRoot.allDependencies) {
                // Ignore project dependencies (transitive deps of projects will be reported with project)
                if (isProject(dependency)) continue

                val source = dependency.source
                val dependencyCollector = manifestDependencies.getOrPut(source.id) { DependencyCollector(source.path) }
                dependencyCollector.addResolved(dependency)
            }
        }

        val manifests = manifestDependencies.mapValues { (name, collector) ->
            val manifestFile = buildLayout.getBuildFile(collector.path)

            GitHubManifest(
                name,
                collector.getDependencies(),
                manifestFile
            )
        }
        return GitHubRepositorySnapshot(
            job = job,
            sha = snapshotParams.gitSha,
            ref = snapshotParams.gitRef,
            detector = detector,
            manifests = manifests.toSortedMap()
        )
    }

    // TODO:DAZ Model this better
    private fun isProject(dependency: ResolvedDependency): Boolean {
        return dependency.id.startsWith("project ")
    }

    private class DependencyCollector(val path: String) {
        private val dependencyBuilders: MutableMap<String, GitHubDependencyBuilder> = mutableMapOf()

        /**
         * Merge each resolved component with the same ID into a single GitHubDependency.
         */
        fun addResolved(component: ResolvedDependency) {
            val dep = dependencyBuilders.getOrPut(component.id) {
                GitHubDependencyBuilder(component.packageUrl())
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
