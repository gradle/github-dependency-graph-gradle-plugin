package org.gradle.github.dependency.extractor.internal

import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.github.dependency.extractor.internal.model.ComponentCoordinates
import org.gradle.github.dependency.extractor.internal.model.ResolvedComponent
import org.gradle.github.dependency.extractor.internal.model.ResolvedConfiguration
import org.gradle.internal.operations.*
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
            >(buildOperation, finishEvent) { details, result -> extractConfigurationDependencies(details, result) }

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
                gitHubRepositorySnapshotBuilder.addProject(project.identityPath, project.buildFile)
            }
            val newProjects = projects.flatMap { it.children }.toSet()
            recursivelyExtractProjects(newProjects)
        }
        recursivelyExtractProjects(setOf(result.rootProject))
    }

    private fun extractConfigurationDependencies(
        details: ResolveConfigurationDependenciesBuildOperationType.Details,
        result: ResolveConfigurationDependenciesBuildOperationType.Result
    ) {
        val repositoryLookup = RepositoryUrlLookup(details, result)
        val rootComponent = result.rootComponent
        val projectIdentityPath = (rootComponent.id as? DefaultProjectComponentIdentifier)?.identityPath?.path
        @Suppress("FoldInitializerAndIfToElvis") // Purely for documentation purposes
        if (projectIdentityPath == null) {
            // TODO: We can actually handle this case, but it's more complicated than I have time to deal with
            // TODO: See the Gradle Enterprise Build Scan Plugin: `ConfigurationResolutionCapturer_5_0`
            return
        }

        val resolvedConfiguration = ResolvedConfiguration(componentId(rootComponent), projectIdentityPath, details.configurationName)
        walkResolvedComponentResult(rootComponent, repositoryLookup, resolvedConfiguration)

        gitHubRepositorySnapshotBuilder.addResolvedConfiguration(resolvedConfiguration)
    }

    private fun walkResolvedComponentResult(
        component: ResolvedComponentResult,
        repositoryLookup: RepositoryUrlLookup,
        resolvedConfiguration: ResolvedConfiguration
    ) {
        val componentId = componentId(component)
        if (resolvedConfiguration.hasComponent(componentId)) {
            return
        }

        val repositoryUrl = repositoryLookup.doLookup(component)
        val resolvedDependencies = component.dependencies.filterIsInstance<ResolvedDependencyResult>().map { it.selected }

        resolvedConfiguration.components.add(ResolvedComponent(componentId, coordinates(component), repositoryUrl, resolvedDependencies.map { componentId(it) }))

        resolvedDependencies
            .forEach {
                walkResolvedComponentResult(it, repositoryLookup, resolvedConfiguration)
            }
    }

    private fun componentId(component: ResolvedComponentResult): String {
        return component.id.displayName
    }

    private fun coordinates(component: ResolvedComponentResult): ComponentCoordinates {
        // TODO: Consider and handle null moduleVersion
        val moduleVersionIdentifier = component.moduleVersion!!
        return ComponentCoordinates(moduleVersionIdentifier.group, moduleVersionIdentifier.name, moduleVersionIdentifier.version)
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
