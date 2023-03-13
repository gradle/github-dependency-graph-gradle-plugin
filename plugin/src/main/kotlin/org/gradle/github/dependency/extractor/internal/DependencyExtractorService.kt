package org.gradle.github.dependency.extractor.internal

import com.github.packageurl.PackageURLBuilder
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.github.dependency.extractor.internal.json.BaseGitHubManifest
import org.gradle.github.dependency.extractor.internal.json.GitHubDependency
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import java.io.File
import java.net.URI
import java.nio.file.Path
import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType as ResolveConfigurationDependenciesBOT
import org.gradle.initialization.LoadProjectsBuildOperationType as LoadProjectsBOT

abstract class DependencyExtractorService :
    BuildOperationListener,
    AutoCloseable {

    protected abstract val gitHubJobName: String
    protected abstract val gitHubRunNumber: String
    protected abstract val gitSha: String
    protected abstract val gitRef: String
    protected abstract val gitWorkspaceDirectory: Path

    /**
     * Can't use this as a proper input:
     * https://github.com/gradle/gradle/issues/19562
     */
    private var fileWriter: DependencyFileWriter = DependencyFileWriter.create()

    private val gitHubRepositorySnapshotBuilder by lazy {
        GitHubRepositorySnapshotBuilder(
            gitHubJobName = gitHubJobName,
            gitHubRunNumber = gitHubRunNumber,
            gitSha = gitSha,
            gitRef = gitRef,
            gitWorkspaceDirectory = gitWorkspaceDirectory
        )
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
            ResolveConfigurationDependenciesBOT.Result
            >(buildOperation, finishEvent) { details, result -> extractDependencies(details, result) }

        handleBuildOperationType<
            LoadProjectsBOT.Details,
            LoadProjectsBOT.Result>(buildOperation, finishEvent) { details, result -> extractProjects(details, result) }
    }

    private fun extractProjects(
        details: LoadProjectsBOT.Details,
        result: LoadProjectsBOT.Result
    ) {
        tailrec fun recursivelyExtractProjects(projects: Set<LoadProjectsBOT.Result.Project>) {
            if (projects.isEmpty()) return
            projects.forEach { project ->
                gitHubRepositorySnapshotBuilder.addProject(details.buildPath, project.path, project.buildFile)
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
                repositoryLookup::doLookup
            )
        // Feel free to change this. This is free-form and has no spec associated with it.
        val metaData = mapOf(
            "project_path" to details.projectPath,
            "configuration" to details.configurationName,
            "build_path" to details.buildPath,
            "configuration_description" to details.configurationDescription,
            "is_script_configuration" to details.isScriptConfiguration,
            "true_project_path" to trueProjectPath
        )
        val name = "${rootComponentId.displayName} [${details.configurationName}]"
        gitHubRepositorySnapshotBuilder.addManifest(
            name = name,
            buildPath = details.buildPath,
            projectPath = trueProjectPath,
            manifest = BaseGitHubManifest(
                name = name,
                resolved = dependencies,
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
         * Looks up the repository for the given [ResolvedComponentResult].
         */
        fun doLookup(resolvedComponentResult: ResolvedComponentResult): String? {
            // Get the repository id from the result
            val repositoryId = result.getRepositoryId(resolvedComponentResult)
            return repositoryId?.let { getRepositoryUrlForId(it) }
        }
    }

    fun writeAndGetSnapshotFile(): File =
        fileWriter.writeDependencyManifest(gitHubRepositorySnapshotBuilder.build())

    override fun close() {
        writeAndGetSnapshotFile()
    }

    /**
     * Collects all the dependencies on a specific configuration.
     */
    class ConfigurationDependencyCollector {

        private val dependencies: MutableMap<String, GitHubDependency> = mutableMapOf()

        fun walkComponentGraph(
            rootComponent: ResolvedComponentResult,
            repositoryUrlLookup: (ResolvedComponentResult) -> String?
        ) {
            val resolvedDependencies = dependentComponents(rootComponent)
            resolvedDependencies.forEach {
                walkComponent(it, GitHubDependency.Relationship.direct, repositoryUrlLookup)
            }
        }

        private fun walkComponent(
            component: ResolvedComponentResult,
            relationship: GitHubDependency.Relationship,
            repositoryUrlLookup: (ResolvedComponentResult) -> String?
        ) {
            val componentId = component.id.displayName
            if (dependencies.containsKey(componentId)) {
                return
            }

            val resolvedDependencies = dependentComponents(component)

            val repositoryUrl = repositoryUrlLookup(component)
            val thisPurl = component.moduleVersion!!.toPurl(repositoryUrl).toString()
            addDependency(componentId, GitHubDependency(thisPurl, relationship, resolvedDependencies.map { it.id.displayName }, metaDataForComponentIdentifier(component.id)))

            resolvedDependencies.forEach {
                walkComponent(it, GitHubDependency.Relationship.indirect, repositoryUrlLookup)
            }
        }

        private fun dependentComponents(component: ResolvedComponentResult) =
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().map { it.selected }

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

        private fun addDependency(name: String, ghDependency: GitHubDependency) {
            val existing = dependencies[name]
            if (existing != null) {
                if (existing.relationship != GitHubDependency.Relationship.direct) {
                    dependencies[name] = ghDependency
                }
            } else {
                dependencies[name] = ghDependency
            }
        }

        fun dependencies(): Map<String, GitHubDependency> {
            return dependencies
        }
    }

    companion object {
        @JvmStatic
        fun extractDependenciesFromResolvedComponentResult(
            resolvedComponentResult: ResolvedComponentResult,
            repositoryUrlLookup: (ResolvedComponentResult) -> String?
        ): Map<String, GitHubDependency> {
            return ConfigurationDependencyCollector()
                .apply {
                    walkComponentGraph(resolvedComponentResult, repositoryUrlLookup)
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

private inline fun <reified D, reified R> handleBuildOperationType(
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
