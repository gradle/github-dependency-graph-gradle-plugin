/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package org.gradle.github.dependency.extractor

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.github.dependency.extractor.internal.DependencyExtractor
import org.gradle.github.dependency.extractor.internal.DependencyExtractorBuildService
import org.gradle.github.dependency.util.EnvironmentVariableLoader
import org.gradle.github.dependency.util.PluginCompanionUtils
import org.gradle.github.dependency.util.service
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.util.GradleVersion
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A plugin that collects all resolved dependencies in a Gradle build and exports it using the GitHub API format.
 */
class GitHubDependencyExtractorPlugin : Plugin<Gradle> {
    private companion object : PluginCompanionUtils() {
        const val ENV_GITHUB_JOB = "GITHUB_JOB"
        const val ENV_GITHUB_RUN_NUMBER = "GITHUB_RUN_NUMBER"
        const val ENV_GITHUB_REF = "GITHUB_REF"
        const val ENV_GITHUB_SHA = "GITHUB_SHA"

        /**
         * Environment variable should be set to the workspace directory that the Git repository is checked out in.
         */
        const val ENV_GITHUB_WORKSPACE = "GITHUB_WORKSPACE"
    }

    internal lateinit var dependencyExtractorProvider: Provider<out DependencyExtractor>

    override fun apply(gradle: Gradle) {
        val gradleVersion = GradleVersion.current()
        // Create the adapter based upon the version of Gradle
        val applicatorStrategy = when {
            gradleVersion < GradleVersion.version("8.0") -> PluginApplicatorStrategy.LegacyPluginApplicatorStrategy
            else -> PluginApplicatorStrategy.DefaultPluginApplicatorStrategy
        }

        // Create the service
        dependencyExtractorProvider = applicatorStrategy.createExtractorService(gradle)

        gradle.rootProject { project ->
            dependencyExtractorProvider
                .get()
                .setRootProjectBuildDirectory(project.buildDir)
        }

        // Register the service to listen for Build Events
        applicatorStrategy.registerExtractorListener(gradle, dependencyExtractorProvider)

        // Register the shutdown hook that should execute at the completion of the Gradle build.
        applicatorStrategy.registerExtractorServiceShutdown(gradle, dependencyExtractorProvider)
    }

    /**
     * Adapters for creating the [DependencyExtractor] and installing it into [Gradle] based upon the Gradle version.
     */
    private interface PluginApplicatorStrategy {

        fun createExtractorService(
            gradle: Gradle
        ): Provider<out DependencyExtractor>

        fun registerExtractorListener(
            gradle: Gradle,
            extractorServiceProvider: Provider<out DependencyExtractor>
        )

        fun registerExtractorServiceShutdown(
            gradle: Gradle,
            extractorServiceProvider: Provider<out DependencyExtractor>
        )

        object LegacyPluginApplicatorStrategy : PluginApplicatorStrategy, EnvironmentVariableLoader.Legacy {

            override fun createExtractorService(
                gradle: Gradle
            ): Provider<out DependencyExtractor> {
                val providerFactory = gradle.providerFactory

                val gitWorkspaceEnvVar = gradle.loadEnvironmentVariable(ENV_GITHUB_WORKSPACE)

                val gitWorkspaceDirectory = Paths.get(gitWorkspaceEnvVar)
                // Create a constant value that the provider will always return.
                // IE. Memoize the value
                val constantDependencyExtractor = object : DependencyExtractor() {
                    override val gitHubJobName: String
                        get() = gradle.loadEnvironmentVariable(ENV_GITHUB_JOB)
                    override val gitHubRunNumber: String
                        get() = gradle.loadEnvironmentVariable(ENV_GITHUB_RUN_NUMBER)
                    override val gitSha: String
                        get() = gradle.loadEnvironmentVariable(ENV_GITHUB_SHA)
                    override val gitRef: String
                        get() = gradle.loadEnvironmentVariable(ENV_GITHUB_REF)
                    override val gitWorkspaceDirectory: Path
                        get() = gitWorkspaceDirectory
                }
                return providerFactory.provider { constantDependencyExtractor }
            }

            override fun registerExtractorListener(
                gradle: Gradle,
                extractorServiceProvider: Provider<out DependencyExtractor>
            ) {
                gradle
                    .buildOperationListenerManager
                    .addListener(extractorServiceProvider.get())
            }

            override fun registerExtractorServiceShutdown(
                gradle: Gradle,
                extractorServiceProvider: Provider<out DependencyExtractor>
            ) {
                gradle.buildFinished {
                    extractorServiceProvider.get().close()
                    gradle
                        .buildOperationListenerManager
                        .removeListener(extractorServiceProvider.get())
                }
            }
        }

        object DefaultPluginApplicatorStrategy : PluginApplicatorStrategy, EnvironmentVariableLoader.Default {
            private const val SERVICE_NAME = "gitHubDependencyExtractorService"

            override fun createExtractorService(
                gradle: Gradle
            ): Provider<out DependencyExtractor> {
                val objectFactory = gradle.objectFactory
                val gitWorkspaceEnvVar =
                    gradle
                        .loadEnvironmentVariable(ENV_GITHUB_WORKSPACE)
                        .map { File(it) }

                val gitWorkspaceDirectory =
                    gitWorkspaceEnvVar.flatMap {
                        objectFactory.directoryProperty().apply {
                            set(it)
                        }
                    }

                return gradle.sharedServices.registerIfAbsent(
                    SERVICE_NAME,
                    DependencyExtractorBuildService::class.java
                ) { spec ->
                    spec.parameters {
                        it.gitHubJobName.convention(gradle.loadEnvironmentVariable(ENV_GITHUB_JOB))
                        it.gitHubRunNumber.convention(gradle.loadEnvironmentVariable(ENV_GITHUB_RUN_NUMBER))
                        it.gitSha.convention(gradle.loadEnvironmentVariable(ENV_GITHUB_SHA))
                        it.gitRef.convention(gradle.loadEnvironmentVariable(ENV_GITHUB_REF))
                        it.gitWorkspaceDirectory.convention(gitWorkspaceDirectory)
                    }
                }
            }

            override fun registerExtractorListener(
                gradle: Gradle,
                extractorServiceProvider: Provider<out DependencyExtractor>
            ) {
                gradle.service<BuildEventListenerRegistryInternal>()
                    .onOperationCompletion(extractorServiceProvider)
            }

            override fun registerExtractorServiceShutdown(
                gradle: Gradle,
                extractorServiceProvider: Provider<out DependencyExtractor>
            ) {
                // No-op as DependencyExtractorService is Auto-Closable
            }
        }
    }
}
