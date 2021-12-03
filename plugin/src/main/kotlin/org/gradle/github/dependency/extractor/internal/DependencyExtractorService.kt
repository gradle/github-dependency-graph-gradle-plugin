package org.gradle.github.dependency.extractor.internal

import com.github.packageurl.PackageURLBuilder
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
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
import java.io.File

abstract class DependencyExtractorService :
    BuildService<BuildServiceParameters.None>,
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
        val dependencies =
            extractDependenciesFromResolvedComponentResult(
                result.rootComponent,
                GitHubDependency.Relationship.direct
            )
        // (result.rootComponent.dependencies.first() as ResolvedDependencyResult).selected.dependencies

        val name = (details.projectPath ?: "") + details.configurationName
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

    private fun extractRepositories(
        details: ResolveConfigurationDependenciesBuildOperationType.Details,
        result: ResolveConfigurationDependenciesBuildOperationType.Result
    ) {
        val repositoryId = result.getRepositoryId(result.rootComponent)

        if (repositoryId != null) {
//                moduleVersionToRepository.put(result.getRootComponent().getModuleVersion(), repositoryId);
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
        extractRepositories(details, result)
    }

    /**
     * Collects all the dependencies on a specific configuration.
     */
    class ConfigurationDependencyCollector {
        fun addDependency() {
        }
    }

    override fun close() {
        // Generate JSON
        File("github-manifest.json").writeText(JacksonJsonSerializer.serializeToJson(gitHubDependencyGraphBuilder.build()))
    }

    companion object {

        @JvmStatic
        fun extractDependenciesFromResolvedComponentResult(
            resolvedComponentResult: ResolvedComponentResult,
            relationship: GitHubDependency.Relationship
        ): List<GitHubDependency> {
            val dependencies = mutableListOf<GitHubDependency>()
            resolvedComponentResult.dependencies.forEach { dependency ->
                if (dependency is ResolvedDependencyResult) {
                    val moduleVersion = dependency.selected.moduleVersion!!
                    val transitives = extractDependenciesFromResolvedComponentResult(
                        dependency.selected,
                        GitHubDependency.Relationship.indirect
                    )
                    dependencies.add(
                        GitHubDependency(
                            purl = moduleVersion.toPurl(),
                            relationship = relationship,
                            dependencies = transitives.map { it.purl }
                        )
                    )
                    dependencies.addAll(transitives)
                }
            }
            return dependencies
        }

        private fun ModuleVersionIdentifier.toPurl() =
            PackageURLBuilder
                .aPackageURL()
                .withType("maven")
                .withNamespace(group)
                .withName(name)
                .withVersion(version)
                .build()
    }
}
