package org.gradle.github.dependency.extractor.internal.json

data class GitHubJob(
    val id: String,
    val name: String,
    val htmlUrl: String? = null // TODO: This should be filled in
)
