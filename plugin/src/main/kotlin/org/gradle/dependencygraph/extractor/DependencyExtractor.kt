package org.gradle.dependencygraph.extractor

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.api.logging.Logging
import org.gradle.dependencygraph.DependencyGraphRenderer
import org.gradle.dependencygraph.model.*
import org.gradle.dependencygraph.model.DependencyScope.*
import org.gradle.dependencygraph.util.*
import org.gradle.initialization.EvaluateSettingsBuildOperationType
import org.gradle.initialization.LoadProjectsBuildOperationType
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.operations.*
import java.io.File
import java.net.URI
import java.util.*

const val PARAM_INCLUDE_PROJECTS = "DEPENDENCY_GRAPH_INCLUDE_PROJECTS"
const val PARAM_INCLUDE_CONFIGURATIONS = "DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS"
const val PARAM_EXCLUDE_PROJECTS = "DEPENDENCY_GRAPH_EXCLUDE_PROJECTS"
const val PARAM_EXCLUDE_CONFIGURATIONS = "DEPENDENCY_GRAPH_EXCLUDE_CONFIGURATIONS"
const val PARAM_RUNTIME_INCLUDE_PROJECTS = "DEPENDENCY_GRAPH_RUNTIME_INCLUDE_PROJECTS"
const val PARAM_RUNTIME_INCLUDE_CONFIGURATIONS = "DEPENDENCY_GRAPH_RUNTIME_INCLUDE_CONFIGURATIONS"
const val PARAM_RUNTIME_EXCLUDE_PROJECTS = "DEPENDENCY_GRAPH_RUNTIME_EXCLUDE_PROJECTS"
const val PARAM_RUNTIME_EXCLUDE_CONFIGURATIONS = "DEPENDENCY_GRAPH_RUNTIME_EXCLUDE_CONFIGURATIONS"


const val PARAM_REPORT_DIR = "DEPENDENCY_GRAPH_REPORT_DIR"

abstract class DependencyExtractor :
    BuildOperationListener,
    AutoCloseable {

    private val pluginParameters = PluginParameters()

    private var settingsEvaluated = false

    private val resolvedConfigurations = Collections.synchronizedList(mutableListOf<ResolvedConfiguration>())

    private val thrownExceptions = Collections.synchronizedList(mutableListOf<Throwable>())

    var rootProjectBuildDirectory: File? = null

    private val buildLayout = BuildLayout()

    // Properties are lazily initialized so that System Properties are initialized by the time
    // the values are used. This is required due to a bug in older Gradle versions. (https://github.com/gradle/gradle/issues/6825)
    private val configurationFilter by lazy {
        ResolvedConfigurationFilter(pluginParameters)
    }

    private val dependencyGraphReportDir by lazy {
        pluginParameters.loadOptional(PARAM_REPORT_DIR)
    }

    abstract fun getRendererClassName(): String

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
        // This method will never be called when registered in a `BuildServiceRegistry` (i.e. Gradle 6.1 & higher)
        // No-op
    }

    override fun progress(operationIdentifier: OperationIdentifier, progressEvent: OperationProgressEvent) {
        // This method will never be called when registered in a `BuildServiceRegistry` (i.e. Gradle 6.1 & higher)
        // No-op
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        handleBuildOperationType<
            ResolveConfigurationDependenciesBuildOperationType.Details,
            ResolveConfigurationDependenciesBuildOperationType.Result
                >(buildOperation, finishEvent) { details, result -> extractConfigurationDependencies(details, result) }

        handleBuildOperationType<
            LoadProjectsBuildOperationType.Details,
            LoadProjectsBuildOperationType.Result>(buildOperation, finishEvent) { _, result -> extractProjects(result) }

        handleBuildOperationType<
            EvaluateSettingsBuildOperationType.Details,
            EvaluateSettingsBuildOperationType.Result>(buildOperation, finishEvent) { details, _ -> extractSettings(details) }
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

    private inline fun <reified D, reified R> handleBuildOperationTypeRaw(
        buildOperation: BuildOperationDescriptor,
        finishEvent: OperationFinishEvent,
        handler: (details: D, result: R) -> Unit
    ) {
        val details: D? = buildOperation.details.let {
            if (it is D) it else null
        }
        if (details == null) {
            return  // Ignore other build operation types
        }

        val failure = finishEvent.failure
        if (failure != null) {
            throw failure
        }

        val result: R = finishEvent.result as R
        handler(details, result)
    }

    open fun extractSettings(
        details: EvaluateSettingsBuildOperationType.Details
    ) {
        settingsEvaluated = true
        val settingsFile = details.settingsFile
        if (settingsFile != null) {
            buildLayout.addSettings(details.buildPath, settingsFile)
        }
    }

    private fun extractProjects(
        result: LoadProjectsBuildOperationType.Result
    ) {
        tailrec fun recursivelyExtractProjects(projects: Set<LoadProjectsBuildOperationType.Result.Project>) {
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
        val projectIdentityPath = (rootComponent.id as? DefaultProjectComponentIdentifier)?.identityPath?.path

        // At this point, any resolution not bound to a particular project will be assigned to the root "build :"
        // This is because `details.buildPath` is always ':', which isn't correct in a composite build.
        // This is inconsequential for GitHub Dependency Graph, since all dependencies are mapped to a single manifest.
        // It is possible to do better. By tracking the current build operation context, we can assign more precisely.
        // See the Gradle Enterprise Build Scan Plugin: `ConfigurationResolutionCapturer_5_0`
        val rootPath = projectIdentityPath ?: details.buildPath
        val configurationName = details.configurationName

        if (!configurationFilter.include(rootPath, configurationName)) {
            LOGGER.debug("Ignoring resolved configuration: $rootPath - $configurationName")
            return
        }

        val scope = dependencyScope(rootPath, configurationName)

        val rootId = if (projectIdentityPath == null) "build $rootPath" else componentId(rootComponent)
        val rootOrigin = DependencyOrigin(rootId, rootPath)
        val resolvedConfiguration = ResolvedConfiguration(rootOrigin, configurationName, scope)

        for (dependencyComponent in getResolvedDependencies(rootComponent)) {
            val directDep = createComponentNode(
                componentId(dependencyComponent),
                rootOrigin,
                true,
                dependencyComponent,
                repositoryLookup
            )
            resolvedConfiguration.addDependency(directDep)

            walkComponentDependencies(dependencyComponent, directDep.origin, repositoryLookup, resolvedConfiguration)
        }

        resolvedConfigurations.add(resolvedConfiguration)
    }

    private fun dependencyScope(
        rootPath: String,
        configurationName: String
    ): DependencyScope {
        if (configurationFilter.scopesAreConfigured()) {
            return if (configurationFilter.isRuntime(rootPath, configurationName)) Runtime else Development
        }
        return Unknown
    }

    private fun walkComponentDependencies(
        component: ResolvedComponentResult,
        parentOrigin: DependencyOrigin,
        repositoryLookup: RepositoryUrlLookup,
        resolvedConfiguration: ResolvedConfiguration
    ) {
        val componentOrigin = getOrigin(component, parentOrigin)
        val direct = componentOrigin != parentOrigin

        for (dependencyComponent in getResolvedDependencies(component)) {
            val dependencyId = componentId(dependencyComponent)
            if (!resolvedConfiguration.hasDependency(dependencyId)) {
                val dependencyNode =
                    createComponentNode(dependencyId, componentOrigin, direct, dependencyComponent, repositoryLookup)
                resolvedConfiguration.addDependency(dependencyNode)

                walkComponentDependencies(dependencyComponent, componentOrigin, repositoryLookup, resolvedConfiguration)
            }
        }
    }

    private fun getOrigin(component: ResolvedComponentResult, parentOrigin: DependencyOrigin): DependencyOrigin {
        val componentId = component.id
        if (componentId is DefaultProjectComponentIdentifier) {
            return DependencyOrigin(componentId(component), componentId.identityPath.path)
        }
        return parentOrigin
    }

    private fun getResolvedDependencies(component: ResolvedComponentResult): List<ResolvedComponentResult> {
        return component.dependencies.filterIsInstance<ResolvedDependencyResult>().map { it.selected }.filter { it != component }
    }

    private fun createComponentNode(componentId: String, origin: DependencyOrigin, isDirectDependency: Boolean, component: ResolvedComponentResult, repositoryLookup: RepositoryUrlLookup): ResolvedDependency {
        val componentDependencies = component.dependencies.filterIsInstance<ResolvedDependencyResult>().map { componentId(it.selected) }
        val repositoryUrl = repositoryLookup.doLookup(component)
        val isProjectDependency = component.id is ProjectComponentIdentifier
        return ResolvedDependency(
            componentId,
            origin,
            isDirectDependency,
            isProjectDependency,
            coordinates(component),
            repositoryUrl,
            componentDependencies
        )
    }

    private fun componentId(component: ResolvedComponentResult): String {
        return component.id.displayName
    }

    private fun coordinates(component: ResolvedComponentResult): DependencyCoordinates {
        val mv = component.moduleVersion
        return if (mv != null) {
            DependencyCoordinates(mv.group, mv.name, mv.version)
        } else {
            DependencyCoordinates("unknown", "unknown", "unknown")
        }
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

    private fun writeDependencyGraph() {
        val outputDirectory = getOutputDir()
        outputDirectory.mkdirs()
        createRenderer().outputDependencyGraph(pluginParameters, buildLayout, resolvedConfigurations, outputDirectory)
    }

    private fun createRenderer(): DependencyGraphRenderer {
        LOGGER.lifecycle("Constructing renderer: ${getRendererClassName()}")
        return Class.forName(getRendererClassName()).getDeclaredConstructor().newInstance() as DependencyGraphRenderer
    }

    private fun getOutputDir(): File {
        if (dependencyGraphReportDir != null) {
            return File(dependencyGraphReportDir)
        }

        if (rootProjectBuildDirectory == null) {
            throw RuntimeException("Cannot determine report file location")
        }
        return File(
            rootProjectBuildDirectory,
            "reports/dependency-graph-snapshots"
        )
    }

    override fun close() {
        if (thrownExceptions.isNotEmpty()) {
            throw DefaultMultiCauseException(
                "The dependency-graph extractor plugin encountered errors while extracting dependencies. " +
                    "Please report this issue at: https://github.com/gradle/github-dependency-graph-gradle-plugin/issues",
                thrownExceptions
            )
        }

        // We use the absence of Settings Evaluated to determine if the build was loaded from the configuration-cache
        if (!settingsEvaluated) {
            LOGGER.lifecycle(
                "Gradle build state was reused from the configuration-cache: " +
                    "Dependency Graph file will not be generated."
            )
            return
        }
        try {
            writeDependencyGraph()
        } catch (e: RuntimeException) {
            throw DefaultMultiCauseException("Failed to write dependency-graph to file", e)
        }
    }

    companion object {
        private val LOGGER = Logging.getLogger(DependencyExtractor::class.java)
    }
}
