package org.gradle.github.dependencygraph.internal

import org.gradle.github.dependencygraph.internal.model.BuildLayout
import org.gradle.github.dependencygraph.internal.model.ResolvedConfiguration
import org.gradle.github.dependencygraph.internal.util.PluginParameters
import java.io.File

interface DependencyGraphRenderer {
    fun outputDependencyGraph(pluginParameters: PluginParameters,
                              buildLayout: BuildLayout,
                              resolvedConfigurations: MutableList<ResolvedConfiguration>,
                              outputDirectory: File
    )
}