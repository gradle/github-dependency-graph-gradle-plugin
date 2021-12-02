package org.gradle.github.dependency.extractor.internal.json

data class GitHubDependencyGraph(
    val manifests: Map<String, GitHubManifest>
)
