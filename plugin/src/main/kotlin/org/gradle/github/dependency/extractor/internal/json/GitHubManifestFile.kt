package org.gradle.github.dependency.extractor.internal.json

import com.fasterxml.jackson.annotation.JsonProperty

data class GitHubManifestFile(
    @get:JsonProperty("source_location")
    val sourceLocation: String
)
