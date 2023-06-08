package org.gradle.github.dependencygraph.internal.json

import com.fasterxml.jackson.annotation.JsonProperty

data class GitHubManifestFile(
    @get:JsonProperty("source_location")
    val sourceLocation: String
)
