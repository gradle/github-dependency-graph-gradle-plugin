package org.gradle.github.dependency.extractor.internal.json

data class GitHubDependency(
    val package_url: String,
    var relationship: Relationship,
    val dependencies: MutableList<String> = mutableListOf()
) {
    enum class Relationship {
        indirect, direct
    }
}
