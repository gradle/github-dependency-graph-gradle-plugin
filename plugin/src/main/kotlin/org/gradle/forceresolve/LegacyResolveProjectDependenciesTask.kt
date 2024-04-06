package org.gradle.forceresolve

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Not worth caching")
abstract class LegacyResolveProjectDependenciesTask: AbstractResolveProjectDependenciesTask() {

    @TaskAction
    fun action() {
        for (configuration in getReportableConfigurations()) {
            configuration.incoming.resolutionResult.root
        }
    }
}