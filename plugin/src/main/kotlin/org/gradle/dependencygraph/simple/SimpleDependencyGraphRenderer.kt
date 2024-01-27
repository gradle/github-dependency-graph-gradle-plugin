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
        outputDependencyGraph(outputDirectory, resolvedConfigurations)
        outputDependencyScopes(outputDirectory, resolvedConfigurations)
        outputDependencyList(outputDirectory, resolvedConfigurations)
    }

    private fun outputDependencyGraph(
        outputDirectory: File,
        resolvedConfigurations: List<ResolvedConfiguration>
    ) {
        val outputFile = File(outputDirectory, "dependency-graph.json")
        val jsonContent = JacksonJsonSerializer.serializeToJson(resolvedConfigurations)
        outputFile.writeText(jsonContent)
    }

    private fun outputDependencyScopes(
        outputDirectory: File,
        resolvedConfigurations: List<ResolvedConfiguration>
    ) {
        val outputFile = File(outputDirectory, "dependency-scopes.json")
        val dependencyList: MutableMap<String, MutableSet<SimpleDependencyOrigin>> = mutableMapOf()
        for (config in resolvedConfigurations) {
            for (dependency in config.allDependencies) {
                val dependencyOrigins = dependencyList.getOrPut(dependency.id) { mutableSetOf() }
                dependencyOrigins.add(
                    SimpleDependencyOrigin(
                        config.rootOrigin.path,
                        config.configurationName
                    )
                )
            }
        }

        val simpleDependencies = dependencyList.map { (id, origins) ->
            SimpleDependency(id, origins.toList())
        }
        val jsonContent = JacksonJsonSerializer.serializeToJson(simpleDependencies)
        outputFile.writeText(jsonContent)
    }

    private fun outputDependencyList(
        outputDirectory: File,
        resolvedConfigurations: List<ResolvedConfiguration>
    ) {
        val outputFile = File(outputDirectory, "dependency-list.txt")
        val dependencyList = resolvedConfigurations.flatMap { config ->
            config.allDependencies.map {
                "${it.coordinates.group}:${it.coordinates.module}:${it.coordinates.version} ---- ${config.rootOrigin.id} ${config.rootOrigin.path} ${config.configurationName}"
            }
        }.distinct().sorted()

        val listTxt = dependencyList.joinToString(separator = "\n")
        outputFile.writeText(listTxt)
    }
}

data class SimpleDependency(
    val dependency: String,
    val resolvedBy: List<SimpleDependencyOrigin>
)

data class SimpleDependencyOrigin(val path: String, val configuration: String)
