package org.gradle.github.dependencygraph.internal.github.json

data class GitHubDependency(
    val package_url: String,
    val relationship: Relationship,
    val dependencies: List<String>
) {
    enum class Relationship {
        indirect, direct
    }
}
