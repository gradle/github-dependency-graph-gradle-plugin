package org.gradle.forceresolve

import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.serialization.Cached
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Not worth caching")
abstract class ResolveProjectDependenciesTask: AbstractResolveProjectDependenciesTask() {
    private val configurationResolvers = Cached.of { createConfigurationResolvers() }

    private fun createConfigurationResolvers(): List<Provider<ResolvedComponentResult>> {
        return getReportableConfigurations().map {
            it.incoming.resolutionResult.rootComponent
        }
    }

    @TaskAction
    fun action() {
        for (configuration in configurationResolvers.get()) {
            configuration.get()
        }
    }
}