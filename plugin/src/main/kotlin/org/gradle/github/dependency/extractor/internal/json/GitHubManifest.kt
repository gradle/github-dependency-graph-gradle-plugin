package org.gradle.github.dependency.extractor.internal.json

data class GitHubManifest(
    val name: String,
    val file: GitHubManifestFile,
    val resolved: Map<String, GitHubDependency>

)
