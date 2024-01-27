package org.gradle.github.dependencygraph

import org.gradle.dependencygraph.util.PluginParameters
import java.nio.file.Path
import java.nio.file.Paths

const val PARAM_JOB_ID = "GITHUB_DEPENDENCY_GRAPH_JOB_ID"
const val PARAM_JOB_CORRELATOR = "GITHUB_DEPENDENCY_GRAPH_JOB_CORRELATOR"
const val PARAM_GITHUB_REF = "GITHUB_DEPENDENCY_GRAPH_REF"
const val PARAM_GITHUB_SHA = "GITHUB_DEPENDENCY_GRAPH_SHA"
/**
 * Environment variable should be set to the workspace directory that the Git repository is checked out in.
 * This is used to determine relative path to build files referenced in the dependency graph.
 */
const val PARAM_GITHUB_WORKSPACE = "GITHUB_DEPENDENCY_GRAPH_WORKSPACE"

class GitHubSnapshotParams(pluginParameters: PluginParameters) {
    val dependencyGraphJobCorrelator: String = pluginParameters.load(PARAM_JOB_CORRELATOR)
    val dependencyGraphJobId: String =pluginParameters.load(PARAM_JOB_ID)
    val gitSha: String = pluginParameters.load(PARAM_GITHUB_SHA)
    val gitRef: String = pluginParameters.load(PARAM_GITHUB_REF)
    val gitHubWorkspace: Path = Paths.get(pluginParameters.load(PARAM_GITHUB_WORKSPACE))
}

