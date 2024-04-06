package org.gradle.forceresolve

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.dependencygraph.extractor.ResolvedConfigurationFilter
import org.gradle.work.DisableCachingByDefault
import java.lang.reflect.Method

@DisableCachingByDefault(because = "Not worth caching")
abstract class LegacyResolveProjectDependenciesTask: DefaultTask() {
    private val canSafelyBeResolvedMethod: Method? = getCanSafelyBeResolvedMethod()

    @Internal
    var configurationFilter: ResolvedConfigurationFilter? = null

    private fun getReportableConfigurations(): List<Configuration> {
        return project.configurations.filter {
            canBeResolved(it) && configurationFilter!!.include(project.path, it.name)
        }
    }

    /**
     * If `DeprecatableConfiguration.canSafelyBeResolve()` is available, use it.
     * Else fall back to `Configuration.canBeResolved`.
     */
    private fun canBeResolved(configuration: Configuration): Boolean {
        if (canSafelyBeResolvedMethod != null) {
            return canSafelyBeResolvedMethod.invoke(configuration) as Boolean
        }
        return configuration.isCanBeResolved
    }

    @TaskAction
    fun action() {
        for (configuration in getReportableConfigurations()) {
            println("Resolving ${configuration.name}")
            configuration.incoming.resolutionResult.root
        }
    }

    private fun getCanSafelyBeResolvedMethod(): Method? {
        return try {
            val dc = Class.forName("org.gradle.internal.deprecation.DeprecatableConfiguration")
            dc.getMethod("canSafelyBeResolved")
        } catch (e: ReflectiveOperationException) {
            null
        }
    }
}