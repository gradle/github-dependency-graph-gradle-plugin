package org.gradle.github.dependency.uploader

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.github.dependency.extractor.GithubDependencyExtractorPlugin
import org.gradle.github.dependency.extractor.internal.DependencyExtractorService
import org.gradle.github.dependency.uploader.internal.DependencyExtractorUploaderService_6_1
import org.gradle.github.dependency.uploader.internal.DependencyUploaderService
import org.gradle.github.dependency.util.EnvironmentVariableLoader
import org.gradle.github.dependency.util.PluginCompanionUtils
import org.gradle.util.GradleVersion

@Suppress("unused")
class GithubDependencyUploaderPlugin : Plugin<Gradle> {
    internal companion object : PluginCompanionUtils() {
        internal const val ENV_GITHUB_API_URL = "GITHUB_API_URL"
        internal const val ENV_GITHUB_REPOSITORY = "GITHUB_REPOSITORY"
        internal const val ENV_GITHUB_TOKEN = "GITHUB_TOKEN"

        internal const val DEFAULT_GITHUB_API_URL = "https://api.github.com"
    }

    override fun apply(gradle: Gradle) {
        val extractorPlugin =
            gradle.plugins.apply(GithubDependencyExtractorPlugin::class.java)
        val gradleVersion = GradleVersion.current()
        val applicatorStrategy = when {
            gradleVersion >= GradleVersion.version("6.1") -> PluginApplicatorStrategy.Strategy_6_1
            else -> PluginApplicatorStrategy.StrategyDefault
        }
        val uploaderServiceGradle =
            applicatorStrategy.createUploadService(
                gradle,
                extractorPlugin.dependencyExtractorServiceProvider
            )
        applicatorStrategy.registerUploadShutdown(gradle, uploaderServiceGradle)
    }

    interface PluginApplicatorStrategy {
        fun createUploadService(
            gradle: Gradle,
            dependencyExtractorProvider: Provider<out DependencyExtractorService>
        ): Provider<out DependencyUploaderService>

        fun registerUploadShutdown(gradle: Gradle, uploaderServiceProvider: Provider<out DependencyUploaderService>)

        object StrategyDefault : PluginApplicatorStrategy, EnvironmentVariableLoader.LoaderDefault {
            override fun createUploadService(
                gradle: Gradle,
                dependencyExtractorProvider: Provider<out DependencyExtractorService>
            ): Provider<out DependencyUploaderService> {
                val constantDependencyUploaderService = object : DependencyUploaderService() {
                    override val gitHubAPIUrl: String
                        get() = gradle.loadEnvironmentVariable(ENV_GITHUB_API_URL, DEFAULT_GITHUB_API_URL)
                    override val gitHubRepository: String
                        get() = gradle.loadEnvironmentVariable(ENV_GITHUB_REPOSITORY)
                    override val gitHubToken: String
                        get() = gradle.loadEnvironmentVariable(ENV_GITHUB_TOKEN)
                }
                constantDependencyUploaderService.dependencyExtractorServiceProvider = dependencyExtractorProvider
                return gradle.providerFactory.provider { constantDependencyUploaderService }
            }

            override fun registerUploadShutdown(
                gradle: Gradle,
                uploaderServiceProvider: Provider<out DependencyUploaderService>
            ) {
                gradle.buildFinished {
                    uploaderServiceProvider.get().close()
                }
            }
        }

        @Suppress("ClassName")
        object Strategy_6_1 : PluginApplicatorStrategy, EnvironmentVariableLoader.Loader_6_1 {
            private const val SERVICE_NAME = "gitHubDependencyUploaderService"

            override fun createUploadService(
                gradle: Gradle,
                dependencyExtractorProvider: Provider<out DependencyExtractorService>
            ): Provider<out DependencyUploaderService> {
                return gradle.sharedServices.registerIfAbsent(
                    SERVICE_NAME,
                    DependencyExtractorUploaderService_6_1::class.java
                ) { spec ->
                    spec.parameters {
                        it.gitHubApiUrl.convention(
                            gradle.loadEnvironmentVariable(
                                ENV_GITHUB_API_URL,
                                DEFAULT_GITHUB_API_URL
                            )
                        )
                        it.gitHubRepository.convention(gradle.loadEnvironmentVariable(ENV_GITHUB_REPOSITORY))
                        it.gitHubToken.convention(gradle.loadEnvironmentVariable(ENV_GITHUB_TOKEN))
                    }
                }.map { it.apply { dependencyExtractorServiceProvider = dependencyExtractorProvider } }
            }

            override fun registerUploadShutdown(
                gradle: Gradle,
                uploaderServiceProvider: Provider<out DependencyUploaderService>
            ) {
                // Force the service to get called once, so it actually gets the 'close' called on it.
                uploaderServiceProvider.get()
            }
        }
    }
}
