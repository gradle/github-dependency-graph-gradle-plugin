package org.gradle.github.dependency.extractor.internal.json

import com.github.packageurl.PackageURL
import org.gradle.github.dependency.extractor.internal.json.GitHubDependency.Relationship

data class GitHubDependency(
    val purl: PackageURL,
    val relationship: Relationship,
    val dependencies: List<String>
) {
    enum class Relationship {
        indirect, direct
    }
}
