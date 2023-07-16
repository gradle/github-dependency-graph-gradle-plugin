package org.gradle.github.dependencygraph.internal.github

import org.gradle.github.dependencygraph.internal.github.json.*
import org.gradle.github.dependencygraph.internal.model.ResolvedDependency
import org.gradle.github.dependencygraph.internal.model.ResolvedConfiguration
import org.gradle.github.dependencygraph.internal.model.BuildLayout

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

    fun buildManifest(manifestName: String, resolvedConfigurations: MutableList<ResolvedConfiguration>, buildLayout: BuildLayout): GitHubManifest {
        val dependencyCollector = DependencyCollector()

        for (resolutionRoot in resolvedConfigurations) {
            for (dependency in resolutionRoot.allDependencies) {
                // Ignore project dependencies (transitive deps of projects will be reported with project)
                if (isProject(dependency)) continue

                dependencyCollector.addResolved(dependency)
            }
        }

        val buildPath = ":"

        val manifestFile = buildLayout.getBuildFile(buildPath)
        return GitHubManifest(
            manifestName,
            dependencyCollector.getDependencies(),
            manifestFile
        )
    }

    // TODO:DAZ Model this better
    private fun isProject(dependency: ResolvedDependency): Boolean {
        return dependency.id.startsWith("project ")
    }

    fun buildSnapshot(manifest: GitHubManifest): GitHubRepositorySnapshot {
        return GitHubRepositorySnapshot(
            job = job,
            sha = snapshotParams.gitSha,
            ref = snapshotParams.gitRef,
            detector = detector,
            manifests = mapOf(manifest.name to manifest)
        )
    }

    private class DependencyCollector {
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
