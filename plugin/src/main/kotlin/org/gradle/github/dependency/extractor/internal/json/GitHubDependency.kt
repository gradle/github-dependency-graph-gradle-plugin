package org.gradle.github.dependency.extractor.internal.json

data class GitHubDependency(
    val purl: String,
    val relationship: Relationship,
    val dependencies: List<String>,
    val metadata: Map<String, Any>
) {
    enum class Relationship {
        indirect, direct
    }
}
