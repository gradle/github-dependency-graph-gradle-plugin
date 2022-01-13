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
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.github.dependency.extractor.internal.json.BaseGitHubManifest
import org.gradle.github.dependency.extractor.internal.json.GitHubDependency
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.util.GradleVersion
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.nio.file.Path
import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType as ResolveConfigurationDependenciesBOT
import org.gradle.initialization.LoadProjectsBuildOperationType as LoadProjectsBOT

abstract class DependencyExtractorService :
    BuildOperationListener,
    AutoCloseable {

    protected abstract val gitWorkspaceDirectory: Path

    /**
     * Can't use this as a proper input:
     * https://github.com/gradle/gradle/issues/19562
     */
    private var fileWriter: DependencyFileWriter = DependencyFileWriter.create()

    private val gitHubDependencyGraphBuilder by lazy {
        GitHubDependencyGraphBuilder(gitWorkspaceDirectory)
    }

    init {
        println("Creating: DependencyExtractorService")
    }

    internal fun setRootProjectBuildDirectory(rootProjectBuildDirectory: File) {
        fileWriter = DependencyFileWriter.create(rootProjectBuildDirectory)
    }

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
        // This method will never be called when registered in a `BuildServiceRegistry` (ie. Gradle 6.1 & higher)
        // No-op
    }

    override fun progress(operationIdentifier: OperationIdentifier, progressEvent: OperationProgressEvent) {
        // This method will never be called when registered in a `BuildServiceRegistry` (ie. Gradle 6.1 & higher)
        // No-op
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        handleBuildOperationType<
            ResolveConfigurationDependenciesBOT.Details,
            ResolveConfigurationDependenciesBOT.Result,
            ResolveConfigurationDependenciesBOT
            >(buildOperation, finishEvent) { details, result -> extractDependencies(details, result) }

        handleBuildOperationType<
            LoadProjectsBOT.Details,
            LoadProjectsBOT.Result,
            LoadProjectsBOT>(buildOperation, finishEvent) { details, result -> extractProjects(details, result) }
    }

    private fun extractProjects(
        details: LoadProjectsBOT.Details,
        result: LoadProjectsBOT.Result
    ) {
        tailrec fun recursivelyExtractProjects(projects: Set<LoadProjectsBOT.Result.Project>) {
            if (projects.isEmpty()) return
            projects.forEach { project ->
                gitHubDependencyGraphBuilder.addProject(details.buildPath, project.path, project.buildFile)
            }
            val newProjects = projects.flatMap { it.children }.toSet()
            recursivelyExtractProjects(newProjects)
        }
        recursivelyExtractProjects(setOf(result.rootProject))
    }

    private fun extractDependencies(
        details: ResolveConfigurationDependenciesBOT.Details,
        result: ResolveConfigurationDependenciesBOT.Result
    ) {
        val rootComponentId = result.rootComponent.id
        val trueProjectPath =
            (rootComponentId as? ProjectComponentIdentifier)?.projectPath
                ?: details.projectPath
        @Suppress("FoldInitializerAndIfToElvis") // Purely for documentation purposes
        if (trueProjectPath == null) {
            // TODO: We can actually handle this case, but it's more complicated than I have time to deal with
            // TODO: See the Gradle Enterprise Build Scan Plugin: `ConfigurationResolutionCapturer_5_0`
            return
        }

        val repositoryLookup = RepositoryUrlLookup(details, result)
        val dependencies =
            extractDependenciesFromResolvedComponentResult(
                result.rootComponent,
                GitHubDependency.Relationship.direct,
                repositoryLookup::doLookup
            )
        val metaData = mapOf(
            "project_path" to details.projectPath,
            "configuration" to details.configurationName,
            "build_path" to details.buildPath,
            "configuration_description" to details.configurationDescription,
            "is_script_configuration" to details.isScriptConfiguration,
            "true_project_path" to trueProjectPath
        )
        val name = buildString {
            /*
             * For project dependencies, create a name like:
             * `Build: :, Project: :, Configuration: runtimeClasspath`
             * For buildscript dependencies, create a name like:
             * `Build: :, Project: :, Buildscript Configuration: classpath`
             */
            append("Build: ${details.buildPath}, Project: ${trueProjectPath},")
            if (details.projectPath == null) {
                append(" Buildscript")
            }
            append(" Configuration: ${details.configurationName}")
        }
        gitHubDependencyGraphBuilder.addManifest(
            name = name,
            buildPath = details.buildPath,
            projectPath = trueProjectPath,
            manifest = BaseGitHubManifest(
                name = name,
                resolved = dependencies.associateBy { it.purl.toString() },
                metadata = metaData
            )
        )
    }

    private class RepositoryUrlLookup(
        private val details: ResolveConfigurationDependenciesBOT.Details,
        private val result: ResolveConfigurationDependenciesBOT.Result
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

    override fun close() {
        fileWriter.writeDependencyManifest(gitHubDependencyGraphBuilder.build())
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
        private val LOGGER = LoggerFactory.getLogger(DependencyExtractorService::class.java)

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

private inline fun <reified D, reified R, BT : BuildOperationType<D, R>> handleBuildOperationType(
    buildOperation: BuildOperationDescriptor,
    finishEvent: OperationFinishEvent,
    handler: (details: D, result: R) -> Unit
) {
    val details: D? = buildOperation.details.let {
        if (it is D) it else null
    }
    val result: R? = finishEvent.result.let {
        if (it is R) it else null
    }
    if (details == null && result == null) {
        return
    } else if (details == null || result == null) {
        throw IllegalStateException("buildOperation.details & finishedEvent.result were unexpected types")
    }
    handler(details, result)
}
