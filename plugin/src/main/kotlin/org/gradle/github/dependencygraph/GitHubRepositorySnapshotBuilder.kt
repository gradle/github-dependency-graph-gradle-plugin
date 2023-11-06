package org.gradle.github.dependencygraph

import org.gradle.dependencygraph.model.ResolvedDependency
import org.gradle.dependencygraph.model.ResolvedConfiguration
import org.gradle.dependencygraph.model.BuildLayout
import org.gradle.github.dependencygraph.model.*

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

    fun buildManifest(manifestName: String, resolvedConfigurations: List<ResolvedConfiguration>, buildLayout: BuildLayout): GitHubManifest {
        val dependencyCollector = DependencyCollector()

        for (resolutionRoot in resolvedConfigurations) {
            for (dependency in resolutionRoot.allDependencies) {
                // Ignore project dependencies (transitive deps of projects will be reported with project)
                if (dependency.isProject) continue

                dependencyCollector.addResolved(dependency)
            }
        }

        return GitHubManifest(
            manifestName,
            dependencyCollector.getDependencies(),
            getManifestFile(buildLayout)
        )
    }

    /**
     * Manifest file is the root build settings file if it exists, or the root build file if not.
     */
    private fun getManifestFile(buildLayout: BuildLayout): GitHubManifestFile? {
        val path = buildLayout.getRootBuildPath()
        return path?.let {
            val filePath = snapshotParams.gitHubWorkspace
                .relativize(path)
                .toString()
                .replace('\\', '/') // Clean up path for Windows systems
            GitHubManifestFile(sourceLocation = filePath)
        }
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
            if (component.isDirect) GitHubDependency.Relationship.direct else GitHubDependency.Relationship.indirect

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
