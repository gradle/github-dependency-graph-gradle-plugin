package org.gradle.forceresolve

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Internal
import org.gradle.dependencygraph.extractor.ResolvedConfigurationFilter
import org.gradle.work.DisableCachingByDefault
import java.lang.reflect.Method

@DisableCachingByDefault(because = "Not worth caching")
abstract class AbstractResolveProjectDependenciesTask : DefaultTask() {
    private val canSafelyBeResolvedMethod: Method? = getCanSafelyBeResolvedMethod()

    @Internal
    var configurationFilter: ResolvedConfigurationFilter? = null

    @Internal
    protected fun getReportableConfigurations(): List<Configuration> {
        return project.configurations.filter {
            canSafelyBeResolved(it) && configurationFilter!!.include(project.path, it.name)
        }
    }

    /**
     * If `DeprecatableConfiguration.canSafelyBeResolved()` is available, use it.
     * Else fall back to `Configuration.canBeResolved`.
     */
    private fun canSafelyBeResolved(configuration: Configuration): Boolean {
        if (canSafelyBeResolvedMethod != null) {
            return canSafelyBeResolvedMethod.invoke(configuration) as Boolean
        }
        return configuration.isCanBeResolved
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