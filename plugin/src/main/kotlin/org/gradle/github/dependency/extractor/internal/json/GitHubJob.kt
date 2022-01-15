package org.gradle.github.dependency.extractor.internal.json

import com.fasterxml.jackson.annotation.JsonProperty

data class GitHubJob(
    val id: String,
    val name: String,
    /**
     * Set this with `$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID`
     */
    @get:JsonProperty("html_url")
    val htmlUrl: String? = null // TODO: This should be filled in
)
