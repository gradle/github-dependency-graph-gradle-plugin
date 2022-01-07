package org.gradle.github.dependency.extractor.internal.json

import com.fasterxml.jackson.annotation.JsonUnwrapped

data class BaseGitHubManifest(
    val name: String,
    val resolved: Map<String, GitHubDependency>,
    val metadata: Map<String, Any?>
)

data class GitHubManifest(
    @JsonUnwrapped val base: BaseGitHubManifest,
    val file: GitHubManifestFile?
)
