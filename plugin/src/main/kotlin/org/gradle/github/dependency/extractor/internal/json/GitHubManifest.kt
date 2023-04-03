package org.gradle.github.dependency.extractor.internal.json

data class GitHubManifest(
    val name: String,
    val resolved: Map<String, GitHubDependency>,
    val file: GitHubManifestFile?
)
