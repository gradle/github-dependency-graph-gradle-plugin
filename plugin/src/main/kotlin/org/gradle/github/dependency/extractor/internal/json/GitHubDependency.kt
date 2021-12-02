package org.gradle.github.dependency.extractor.internal.json

import com.github.packageurl.PackageURL

data class GitHubDependency(
    val purl: String,
    val relationship: Relationship,
    val dependencies: List<String>
) {

    enum class Relationship {
        indirect, direct
    }

    companion object {
        operator fun invoke(
            purl: PackageURL,
            relationship: Relationship,
            dependencies: List<String>
        ) = GitHubDependency(purl.toString(), relationship, dependencies)
    }
}
