package org.gradle.dependencygraph.extractor

class ResolvedConfigurationFilter(projectFilter: String?, configurationFilter: String?) {
    private val projectFilter = projectFilter?.toRegex()
    private val configurationFilter = configurationFilter?.toRegex()

    fun include(projectPath: String, configurationName: String): Boolean {
        if (projectFilter != null && !projectFilter.matches(projectPath)) {
            return false
        }
        if (configurationFilter != null && !configurationFilter.matches(configurationName)) {
            return false
        }
        return true
    }
}