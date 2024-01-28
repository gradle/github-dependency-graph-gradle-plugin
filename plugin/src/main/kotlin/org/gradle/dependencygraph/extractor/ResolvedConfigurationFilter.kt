package org.gradle.dependencygraph.extractor

import org.gradle.dependencygraph.util.PluginParameters

class ResolvedConfigurationFilter(pluginParameters: PluginParameters) {
    private val includeProjects = pluginParameters.loadOptional(PARAM_INCLUDE_PROJECTS)?.toRegex()
    private val includeConfigurations = pluginParameters.loadOptional(PARAM_INCLUDE_CONFIGURATIONS)?.toRegex()
    private val excludeProjects = pluginParameters.loadOptional(PARAM_EXCLUDE_PROJECTS)?.toRegex()
    private val excludeConfigurations = pluginParameters.loadOptional(PARAM_EXCLUDE_CONFIGURATIONS)?.toRegex()

    private val runtimeIncludeProjects = pluginParameters.loadOptional(PARAM_RUNTIME_INCLUDE_PROJECTS)?.toRegex()
    private val runtimeIncludeConfigurations = pluginParameters.loadOptional(PARAM_RUNTIME_INCLUDE_CONFIGURATIONS)?.toRegex()
    private val runtimeExcludeProjects = pluginParameters.loadOptional(PARAM_RUNTIME_EXCLUDE_PROJECTS)?.toRegex()
    private val runtimeExcludeConfigurations = pluginParameters.loadOptional(PARAM_RUNTIME_EXCLUDE_CONFIGURATIONS)?.toRegex()

    fun include(projectPath: String, configurationName: String): Boolean {
        return includes(includeProjects, projectPath)
            && notExcludes(excludeProjects, projectPath)
            && includes(includeConfigurations, configurationName)
            && notExcludes(excludeConfigurations, configurationName)
    }

    fun scopesAreConfigured(): Boolean {
        return runtimeIncludeProjects != null
            || runtimeIncludeConfigurations != null
            || runtimeExcludeProjects != null
            || runtimeExcludeConfigurations != null
    }

    fun isRuntime(projectPath: String, configurationName: String): Boolean {
        return includes(runtimeIncludeProjects, projectPath)
            && notExcludes(runtimeExcludeProjects, projectPath)
            && includes(runtimeIncludeConfigurations, configurationName)
            && notExcludes(runtimeExcludeConfigurations, configurationName)
    }

    private fun includes(regex: Regex?, value: String): Boolean {
        return regex == null || regex.matches(value)
    }

    private fun notExcludes(regex: Regex?, value: String): Boolean {
        return regex == null || !regex.matches(value)
    }
}