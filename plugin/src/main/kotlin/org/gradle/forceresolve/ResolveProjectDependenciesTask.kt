package org.gradle.forceresolve

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.dependencygraph.extractor.ResolvedConfigurationFilter
import org.gradle.internal.serialization.Cached
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Not worth caching")
abstract class ResolveProjectDependenciesTask: DefaultTask() {
    private val configurationResolvers = Cached.of { createConfigurationResolvers() }

    @Internal
    var configurationFilter: ResolvedConfigurationFilter? = null

    private fun createConfigurationResolvers(): List<Provider<ResolvedComponentResult>> {
        return getReportableConfigurations().map {
            it.incoming.resolutionResult.rootComponent
        }
    }

    private fun getReportableConfigurations(): List<Configuration> {
        return project.configurations.filter {
            it.isCanBeResolved && configurationFilter!!.include(project.path, it.name)
        }
    }

    @TaskAction
    fun action() {
        for (configuration in configurationResolvers.get()) {
            configuration.get()
        }
    }
}