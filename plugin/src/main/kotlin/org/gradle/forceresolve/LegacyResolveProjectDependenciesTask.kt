package org.gradle.forceresolve

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Not worth caching")
abstract class LegacyResolveProjectDependenciesTask: DefaultTask() {
    private fun getReportableConfigurations(): List<Configuration> {
        return project.configurations.filter { it.isCanBeResolved }
    }

    @TaskAction
    fun action() {
        for (configuration in getReportableConfigurations()) {
            configuration.incoming.resolutionResult.root
        }
    }
}