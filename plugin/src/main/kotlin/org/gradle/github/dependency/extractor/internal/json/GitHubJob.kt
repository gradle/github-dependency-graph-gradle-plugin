package org.gradle.github.dependency.extractor.internal.json

import com.fasterxml.jackson.annotation.JsonProperty

data class GitHubJob(
    val id: String,
    val name: String,
    @get:JsonProperty("html_url")
    val htmlUrl: String? = null // TODO: This should be filled in
)
