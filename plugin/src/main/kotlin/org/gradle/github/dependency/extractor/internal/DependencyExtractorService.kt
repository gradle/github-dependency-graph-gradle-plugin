package org.gradle.github.dependency.extractor.internal

import com.github.packageurl.PackageURL
import com.github.packageurl.PackageURLBuilder
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.github.dependency.extractor.internal.json.GitHubDependency
import org.gradle.github.dependency.extractor.internal.json.GitHubManifest
import org.gradle.github.dependency.extractor.internal.json.GitHubManifestFile
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.util.GradleVersion
import java.io.File
import java.net.URI

abstract class DependencyExtractorService :
    BuildOperationListener,
    AutoCloseable {

    private val gitHubDependencyGraphBuilder = GitHubDependencyGraphBuilder()

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
        // No-op
    }

    override fun progress(operationIdentifier: OperationIdentifier, progressEvent: OperationProgressEvent) {
        // No-op
    }

    private fun extractDependencies(
        details: ResolveConfigurationDependenciesBuildOperationType.Details,
        result: ResolveConfigurationDependenciesBuildOperationType.Result
    ) {
        val repositoryLookup = RepositoryUrlLookup(details, result)
        val dependencies =
            extractDependenciesFromResolvedComponentResult(
                result.rootComponent,
                GitHubDependency.Relationship.direct,
                repositoryLookup::doLookup
            )
        // TODO: Remove this debug logging, potentially add it as meta-data to the JSON output
        println("Project Path: ${details.projectPath}")
        println("Configuration: ${details.configurationName}")
        println("Build Path: ${details.buildPath}")
        val name = (details.projectPath ?: "") + ':' + details.configurationName
        gitHubDependencyGraphBuilder.addManifest(
            name, GitHubManifest(
                name = name,
                file = GitHubManifestFile(
                    "build.gradle.kts"
                ),
                resolved = dependencies.associateBy { it.purl.toString() }
            )
        )
    }

    private class RepositoryUrlLookup(
        private val details: ResolveConfigurationDependenciesBuildOperationType.Details,
        private val result: ResolveConfigurationDependenciesBuildOperationType.Result
    ) {

        private fun getRepositoryUrlForId(id: String): String? {
            return details
                .repositories
                ?.find { it.id == id }
                ?.properties
                ?.let { it["URL"] as? URI }
                ?.toURL()
                ?.toString()
        }

        /**
         * Looks up the repository for the given [ResolvedDependencyResult].
         */
        fun doLookup(resolvedDependencyResult: ResolvedDependencyResult): String? {
            // Get the repository id from the result
            val repositoryId = result.getRepositoryId(resolvedDependencyResult.selected)
            return repositoryId?.let { getRepositoryUrlForId(it) }
        }
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        val details: ResolveConfigurationDependenciesBuildOperationType.Details? = buildOperation.details.let {
            if (it is ResolveConfigurationDependenciesBuildOperationType.Details) it else null
        }
        val result: ResolveConfigurationDependenciesBuildOperationType.Result? = finishEvent.result.let {
            if (it is ResolveConfigurationDependenciesBuildOperationType.Result) it else null
        }
        if (details == null && result == null) {
            return
        } else if (details == null || result == null) {
            throw IllegalStateException("buildOperation.details & finishedEvent.result were unexpected types")
        }
        extractDependencies(details, result)
    }

    override fun close() {
        // Generate JSON
        File("github-manifest.json").writeText(JacksonJsonSerializer.serializeToJson(gitHubDependencyGraphBuilder.build()))
    }

    /**
     * Collects all the dependencies on a specific configuration.
     */
    class ConfigurationDependencyCollector {

        private val variantExtractor by lazy {
            if (GradleVersion.current() >= GradleVersion.version("5.6")) {
                VariantExtractor.VariantExtractor_5_6
            } else {
                VariantExtractor.Default
            }
        }
        private val dependencies: MutableMap<PackageURL, GitHubDependency> = mutableMapOf()

        fun walkResolveComponentResults(
            resolvedComponentResult: ResolvedComponentResult,
            selectedVariant: ResolvedVariantResult? = null,
            relationship: GitHubDependency.Relationship,
            repositoryUrlLookup: (ResolvedDependencyResult) -> String?
        ): List<PackageURL> {
            val dependencyPurls = mutableListOf<PackageURL>()
            variantExtractor
                .getDependencies(selectedVariant, resolvedComponentResult)
                .forEach { dependency ->
                    // We only care about resolve dependencies
                    if (dependency is ResolvedDependencyResult) {
                        val selected = dependency.selected
                        val moduleVersion = selected.moduleVersion!!
                        val resolvedSelectedVariant = variantExtractor.getSelectedVariant(dependency)
                        val transitives = walkResolveComponentResults(
                            selected,
                            resolvedSelectedVariant,
                            GitHubDependency.Relationship.indirect,
                            repositoryUrlLookup
                        )
                        val repositoryUrl = repositoryUrlLookup(dependency)
                        val thisPurl = moduleVersion.toPurl(repositoryUrl)
                        addDependency(
                            GitHubDependency(
                                thisPurl,
                                relationship,
                                transitives.map { it.toString() },
                                metaDataForComponentIdentifier(dependency.selected.id)
                            )
                        )
                        dependencyPurls.add(thisPurl)
                    }
                }
            return dependencyPurls
        }

        private fun metaDataForComponentIdentifier(componentIdentifier: ComponentIdentifier): Map<String, Any> {
            return when (componentIdentifier) {
                is ProjectComponentIdentifier -> mapOf(
                    "name" to componentIdentifier.build.name,
                    "projectPath" to componentIdentifier.projectPath
                )
                is ModuleComponentIdentifier -> mapOf(
                    "group" to componentIdentifier.group,
                    "module" to componentIdentifier.module,
                    "version" to componentIdentifier.version
                )
                else -> mapOf(
                    "display_name" to componentIdentifier.displayName,
                    "class" to componentIdentifier.javaClass.name
                )
            }
        }

        private fun addDependency(ghDependency: GitHubDependency) {
            val purl = ghDependency.purl
            val existing = dependencies[purl]
            if (existing != null) {
                if (existing.relationship != GitHubDependency.Relationship.direct) {
                    dependencies[purl] = ghDependency
                }
            } else {
                dependencies[purl] = ghDependency
            }
        }

        fun dependencies(): List<GitHubDependency> {
            return dependencies.values.toList()
        }
    }

    companion object {

        @JvmStatic
        fun extractDependenciesFromResolvedComponentResult(
            resolvedComponentResult: ResolvedComponentResult,
            relationship: GitHubDependency.Relationship,
            repositoryUrlLookup: (ResolvedDependencyResult) -> String?
        ): List<GitHubDependency> {
            return ConfigurationDependencyCollector()
                .apply {
                    walkResolveComponentResults(
                        resolvedComponentResult = resolvedComponentResult,
                        relationship = relationship,
                        repositoryUrlLookup = repositoryUrlLookup
                    )
                }
                .dependencies()
        }

        private fun ModuleVersionIdentifier.toPurl(repositoryUrl: String?) =
            PackageURLBuilder
                .aPackageURL()
                .withType("maven")
                .withNamespace(group)
                .withName(name)
                .withVersion(version)
                .also {
                    if (repositoryUrl != null) {
                        it.withQualifier("repository_url", repositoryUrl)
                    }
                }
                .build()
    }
}
