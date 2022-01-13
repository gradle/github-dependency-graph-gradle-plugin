package org.gradle.github.dependency.extractor.internal.json

data class GitHubRepositorySnapshot(
    val version: Int = 0,
    val job: GitHubJob,
    val sha: String,
    val ref: String,
    val detector: GitHubDetector,
    val manifests: Map<String, GitHubManifest>
)
