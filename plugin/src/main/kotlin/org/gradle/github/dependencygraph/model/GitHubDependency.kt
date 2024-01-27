package org.gradle.github.dependencygraph.model

data class GitHubDependency(
    val package_url: String,
    val relationship: Relationship,
    val scope: Scope,
    val dependencies: List<String>
) {
    enum class Relationship {
        indirect, direct
    }
    enum class Scope {
        runtime, development
    }
}
