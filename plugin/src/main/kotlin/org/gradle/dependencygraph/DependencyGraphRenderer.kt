package org.gradle.dependencygraph

import org.gradle.dependencygraph.model.BuildLayout
import org.gradle.dependencygraph.model.ResolvedConfiguration
import org.gradle.dependencygraph.util.PluginParameters
import java.io.File

interface DependencyGraphRenderer {
    fun outputDependencyGraph(
        pluginParameters: PluginParameters,
        buildLayout: BuildLayout,
        resolvedConfigurations: List<ResolvedConfiguration>,
        outputDirectory: File
    )
}