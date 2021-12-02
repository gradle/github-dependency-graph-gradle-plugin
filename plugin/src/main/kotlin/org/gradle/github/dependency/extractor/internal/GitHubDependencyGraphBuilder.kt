package org.gradle.github.dependency.extractor.internal

import org.gradle.github.dependency.extractor.internal.json.GitHubDependencyGraph
import org.gradle.github.dependency.extractor.internal.json.GitHubManifest
import java.util.concurrent.ConcurrentHashMap

class GitHubDependencyGraphBuilder {

    private val manifests: MutableMap<String, GitHubManifest> = ConcurrentHashMap<String, GitHubManifest>()

    fun addManifest(name: String, manifest: GitHubManifest) {
        manifests[name] = manifest
    }

    fun build(): GitHubDependencyGraph {
        return GitHubDependencyGraph(
            manifests = manifests
        )
    }
}
