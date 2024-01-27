package org.gradle.dependencygraph.simple

import org.gradle.dependencygraph.DependencyGraphRenderer
import org.gradle.dependencygraph.model.BuildLayout
import org.gradle.dependencygraph.model.ResolvedConfiguration
import org.gradle.dependencygraph.model.DependencyScope
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
        val dependencyList: MutableMap<String, MutableSet<SimpleDependencyResolution>> = mutableMapOf()
        for (config in resolvedConfigurations) {
            for (dependency in config.allDependencies) {
                if (dependency.isProject) continue

                val dependencyScopes = dependencyList.getOrPut(dependency.id) { mutableSetOf() }
                dependencyScopes.add(
                    SimpleDependencyResolution(
                        config.rootOrigin.path,
                        config.configurationName,
                        config.scope
                    )
                )
            }
        }

        val simpleDependencies = dependencyList.map { (id, resolutions) ->
            SimpleDependency(id, DependencyScope.getEffectiveScope(resolutions.map {it.scope}), resolutions.toList())
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
                "${it.coordinates.group}:${it.coordinates.module}:${it.coordinates.version}"
            }
        }.distinct().sorted()

        val listTxt = dependencyList.joinToString(separator = "\n")
        outputFile.writeText(listTxt)
    }
}

data class SimpleDependency(
    val dependency: String,
    val effectiveScope: DependencyScope,
    val resolvedBy: List<SimpleDependencyResolution>
)

data class SimpleDependencyResolution(val path: String, val configuration: String, val scope: DependencyScope)
