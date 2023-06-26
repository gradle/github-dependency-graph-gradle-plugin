package org.gradle.github.dependencygraph.internal.json

data class GitHubManifest(
    val name: String,
    val resolved: Map<String, GitHubDependency>,
    val file: GitHubManifestFile?
)
