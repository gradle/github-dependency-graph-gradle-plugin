package org.gradle.dependencygraph.simple

import org.gradle.dependencygraph.DependencyGraphRenderer
import org.gradle.dependencygraph.model.BuildLayout
import org.gradle.dependencygraph.model.ResolvedConfiguration
import org.gradle.dependencygraph.util.JacksonJsonSerializer
import org.gradle.dependencygraph.util.PluginParameters
import java.io.File

/**
 * An example `DependencyGraphRenderer` that outputs the dependency graph in 2 files:
 * - `dependency-graph.json` contains the raw structure of the extracted `ResolvedConfiguration` instances.
 * - `dependency-list.txt` contains a list of coordinates of all resolved dependencies.
 *
 * Note that the structure of `ResolvedConfiguration` is not stable and is subject to change.
 */
class SimpleDependencyGraphRenderer : DependencyGraphRenderer {
    override fun outputDependencyGraph(
        pluginParameters: PluginParameters,
        buildLayout: BuildLayout,
        resolvedConfigurations: List<ResolvedConfiguration>,
        outputDirectory: File
    ) {
        val graphOutputFile = File(outputDirectory, "dependency-graph.json")
        val graphJson = JacksonJsonSerializer.serializeToJson(resolvedConfigurations)
        graphOutputFile.writeText(graphJson)

        val listOutputFile = File(outputDirectory, "dependency-list.txt")
        val dependencyList = resolvedConfigurations.flatMap { it ->
            it.allDependencies.map {
                "${it.coordinates.group}:${it.coordinates.module}:${it.coordinates.version}"
            }
        }.distinct().sorted()

        val listTxt = dependencyList.joinToString(separator = "\n")
        listOutputFile.writeText(listTxt)
    }
}