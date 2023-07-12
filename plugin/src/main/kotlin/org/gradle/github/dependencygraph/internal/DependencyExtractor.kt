package org.gradle.github.dependencygraph.internal

import org.gradle.api.GradleException
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.github.GitHubDependencyGraphPlugin
import org.gradle.github.dependencygraph.internal.model.ComponentCoordinates
import org.gradle.github.dependencygraph.internal.model.ResolutionRoot
import org.gradle.github.dependencygraph.internal.model.ResolvedComponent
import org.gradle.github.dependencygraph.internal.model.ResolvedConfiguration
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.operations.*
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.*
import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType as ResolveConfigurationDependenciesBOT
import org.gradle.initialization.LoadProjectsBuildOperationType as LoadProjectsBOT

abstract class DependencyExtractor :
    BuildOperationListener,
    AutoCloseable {

    protected abstract val dependencyGraphJobCorrelator: String
    protected abstract val dependencyGraphJobId: String
    protected abstract val dependencyGraphReportDir: String
    protected abstract val gitSha: String
    protected abstract val gitRef: String
    protected abstract val gitWorkspaceDirectory: Path

    var rootProjectBuildDirectory: File? = null

    /**
     * Map of all resolved configurations by name
     */
    private val resolvedConfigurations = Collections.synchronizedList(mutableListOf<ResolvedConfiguration>())

    /**
     * Map of the project identifier to the relative path of the git workspace directory [gitWorkspaceDirectory].
     */
    private val buildLayout by lazy {
        BuildLayout(gitWorkspaceDirectory)
    }

    private val thrownExceptions = Collections.synchronizedList(mutableListOf<Throwable>())

    init {
        println("Creating: DependencyExtractorService")
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

    private inline fun <reified D, reified R> handleBuildOperationType(
            buildOperation: BuildOperationDescriptor,
            finishEvent: OperationFinishEvent,
            handler: (details: D, result: R) -> Unit
    ) {
        try {
            handleBuildOperationTypeRaw<D, R>(buildOperation, finishEvent, handler)
        } catch (e: Throwable) {
            thrownExceptions.add(e)
            throw e
        }
    }

    private fun extractProjects(
        @Suppress("UNUSED_PARAMETER") details: LoadProjectsBOT.Details,
        result: LoadProjectsBOT.Result
    ) {
        tailrec fun recursivelyExtractProjects(projects: Set<LoadProjectsBOT.Result.Project>) {
            if (projects.isEmpty()) return
            projects.forEach { project ->
                buildLayout.addProject(project.identityPath, project.buildFile)
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

        if (rootComponent.dependencies.isEmpty()) {
            // No dependencies to extract: can safely ignore
            return
        }

        // TODO: At this point, any resolution not bound to a particular project will be assigned to the root "build :"
        // This is because `details.buildPath` is always ':', which isn't correct in a composite build.
        // It is possible to do better. By tracking the current build operation context, we can assign more precisely.
        // See the Gradle Enterprise Build Scan Plugin: `ConfigurationResolutionCapturer_5_0`
        val projectIdentityPath = (rootComponent.id as? DefaultProjectComponentIdentifier)?.identityPath?.path
        val rootPath = projectIdentityPath ?: details.buildPath
        val rootId = if (projectIdentityPath == null) "build $rootPath" else componentId(rootComponent)
        val resolutionRoot = ResolutionRoot(rootId, rootPath)
        val resolvedConfiguration = ResolvedConfiguration(resolutionRoot)

        for (directDependency in getResolvedDependencies(rootComponent)) {
            val directDep = createComponentNode(
                componentId(directDependency),
                resolutionRoot,
                true,
                directDependency,
                repositoryLookup
            )
            resolvedConfiguration.addDependency(directDep)

            walkComponentDependencies(directDependency, resolutionRoot, repositoryLookup, resolvedConfiguration)
        }

        resolvedConfigurations.add(resolvedConfiguration)
    }

    private fun walkComponentDependencies(
        component: ResolvedComponentResult,
        resolveContext: ResolutionRoot,
        repositoryLookup: RepositoryUrlLookup,
        resolvedConfiguration: ResolvedConfiguration
    ) {
        val rootComponent = getResolutionRoot(component, resolveContext)
        val direct = rootComponent != resolveContext

        val dependencyComponents = getResolvedDependencies(component)
        for (dependencyComponent in dependencyComponents) {
            val dependencyId = componentId(dependencyComponent)
            if (!resolvedConfiguration.hasDependency(dependencyId)) {
                val dependencyNode = createComponentNode(dependencyId, rootComponent, direct, dependencyComponent, repositoryLookup)
                resolvedConfiguration.addDependency(dependencyNode)

                walkComponentDependencies(dependencyComponent, rootComponent, repositoryLookup, resolvedConfiguration)
            }
        }
    }

    private fun getResolutionRoot(component: ResolvedComponentResult, resolveContext: ResolutionRoot): ResolutionRoot {
        val componentId = component.id
        if (componentId is DefaultProjectComponentIdentifier) {
            return ResolutionRoot(componentId(component), componentId.identityPath.path)
        }
        return resolveContext
    }

    private fun getResolvedDependencies(component: ResolvedComponentResult): List<ResolvedComponentResult> {
        return component.dependencies.filterIsInstance<ResolvedDependencyResult>().map { it.selected }.filter { it != component }
    }

    private fun createComponentNode(componentId: String, rootComponent: ResolutionRoot, direct: Boolean, component: ResolvedComponentResult, repositoryLookup: RepositoryUrlLookup): ResolvedComponent {
        val componentDependencies = component.dependencies.filterIsInstance<ResolvedDependencyResult>().map { componentId(it.selected) }
        val repositoryUrl = repositoryLookup.doLookup(component)
        return ResolvedComponent(componentId, rootComponent, direct, coordinates(component), repositoryUrl, componentDependencies)
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
                ?.toString()
                ?.removeSuffix("/")
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

    private fun writeAndGetSnapshotFile() {
        val outputFile = File(getOutputDir(), "${dependencyGraphJobCorrelator}.json")
        val fileWriter = DependencyFileWriter(outputFile)

        val gitHubRepositorySnapshotBuilder =
            GitHubRepositorySnapshotBuilder(
                dependencyGraphJobCorrelator = dependencyGraphJobCorrelator,
                dependencyGraphJobId = dependencyGraphJobId,
                gitSha = gitSha,
                gitRef = gitRef
            )

        val snapshot = gitHubRepositorySnapshotBuilder.build(resolvedConfigurations, buildLayout)
        fileWriter.writeDependencySnapshot(snapshot)
    }

    private fun getOutputDir(): File {
        if (dependencyGraphReportDir.isNotEmpty()) {
            return File(dependencyGraphReportDir)
        }

        if (rootProjectBuildDirectory == null) {
            throw RuntimeException("Cannot determine report file location")
        }
        return File(
            rootProjectBuildDirectory,
            "reports/github-dependency-graph-snapshots"
        )
    }

    override fun close() {
        if (thrownExceptions.isNotEmpty()) {
            throw DefaultMultiCauseException(
                    "The ${GitHubDependencyGraphPlugin::class.simpleName} plugin encountered errors while extracting dependencies. " +
                            "Please report this issue at: https://github.com/gradle/github-dependency-graph-gradle-plugin/issues",
                    thrownExceptions
            )
        }
        try {
            writeAndGetSnapshotFile()
        } catch (e: RuntimeException) {
            throw GradleException(
                    "The ${GitHubDependencyGraphPlugin::class.simpleName} plugin encountered errors while writing the dependency snapshot json file. " +
                            "Please report this issue at: https://github.com/gradle/github-dependency-graph-gradle-plugin/issues",
                    e
            )
        }
    }
}

private inline fun <reified D, reified R> handleBuildOperationTypeRaw(
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
