package org.gradle.github.dependencygraph.model

data class GitHubManifest(
    val name: String,
    val resolved: Map<String, GitHubDependency>,
    val file: GitHubManifestFile?
)
