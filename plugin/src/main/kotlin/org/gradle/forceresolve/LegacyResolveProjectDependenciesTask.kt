package org.gradle.forceresolve

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.dependencygraph.extractor.ResolvedConfigurationFilter
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Not worth caching")
abstract class LegacyResolveProjectDependenciesTask: DefaultTask() {
    @Internal
    var configurationFilter: ResolvedConfigurationFilter? = null

    private fun getReportableConfigurations(): List<Configuration> {
        return project.configurations.filter {
            it.isCanBeResolved && configurationFilter!!.include(project.path, it.name)
        }
    }

    @TaskAction
    fun action() {
        for (configuration in getReportableConfigurations()) {
            configuration.incoming.resolutionResult.root
        }
    }
}