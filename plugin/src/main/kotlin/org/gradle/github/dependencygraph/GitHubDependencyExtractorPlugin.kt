package org.gradle.github.dependencygraph

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.github.dependencygraph.internal.DependencyExtractor
import org.gradle.github.dependencygraph.internal.DependencyExtractorBuildService
import org.gradle.github.dependencygraph.internal.LegacyDependencyExtractor
import org.gradle.github.dependencygraph.internal.util.GradleExtensions
import org.gradle.github.dependencygraph.internal.util.service
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.util.GradleVersion

/**
 * A plugin that collects all resolved dependencies in a Gradle build and exports it using the GitHub API format.
 */
class GitHubDependencyExtractorPlugin : Plugin<Gradle> {
    // Register extension functions on `Gradle` type
    private companion object : GradleExtensions()

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
                .rootProjectBuildDirectory = project.buildDir
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

        object LegacyPluginApplicatorStrategy : PluginApplicatorStrategy {

            override fun createExtractorService(
                gradle: Gradle
            ): Provider<out DependencyExtractor> {
                val dependencyExtractor = LegacyDependencyExtractor()
                return gradle.providerFactory.provider { dependencyExtractor }
            }

            override fun registerExtractorListener(
                gradle: Gradle,
                extractorServiceProvider: Provider<out DependencyExtractor>
            ) {
                gradle.buildOperationListenerManager
                    .addListener(extractorServiceProvider.get())
            }

            override fun registerExtractorServiceShutdown(
                gradle: Gradle,
                extractorServiceProvider: Provider<out DependencyExtractor>
            ) {
                gradle.buildFinished {
                    extractorServiceProvider.get().close()
                    gradle.buildOperationListenerManager
                        .removeListener(extractorServiceProvider.get())
                }
            }
        }

        object DefaultPluginApplicatorStrategy : PluginApplicatorStrategy {
            private const val SERVICE_NAME = "gitHubDependencyExtractorService"

            override fun createExtractorService(
                gradle: Gradle
            ): Provider<out DependencyExtractor> {
                return gradle.sharedServices.registerIfAbsent(
                    SERVICE_NAME,
                    DependencyExtractorBuildService::class.java
                ) {}
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
