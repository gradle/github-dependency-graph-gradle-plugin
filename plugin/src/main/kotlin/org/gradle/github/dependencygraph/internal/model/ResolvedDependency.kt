package org.gradle.github.dependencygraph.internal.model

import com.github.packageurl.PackageURLBuilder

private const val DEFAULT_MAVEN_REPOSITORY_URL = "https://repo.maven.apache.org/maven2"

data class ResolvedDependency(
    val id: String,
    val source: DependencySource,
    val direct: Boolean,
    val coordinates: DependencyCoordinates,
    val repositoryUrl: String?,
    val dependencies: List<String>
)
{
    fun packageUrl() =
        PackageURLBuilder
            .aPackageURL()
            .withType("maven")
            .withNamespace(coordinates.group.ifEmpty { coordinates.module }) // TODO: This is a sign of broken mapping from component -> PURL
            .withName(coordinates.module)
            .withVersion(coordinates.version)
            .also {
                if (repositoryUrl != null && repositoryUrl != DEFAULT_MAVEN_REPOSITORY_URL) {
                    it.withQualifier("repository_url", repositoryUrl)
                }
            }
            .build()
            .toString()

}
