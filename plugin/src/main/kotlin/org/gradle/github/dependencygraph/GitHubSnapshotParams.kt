package org.gradle.github.dependencygraph

import org.gradle.dependencygraph.util.PluginParameters
import java.nio.file.Path
import java.nio.file.Paths

const val PARAM_JOB_ID = "GITHUB_JOB_ID"
const val PARAM_JOB_CORRELATOR = "GITHUB_JOB_CORRELATOR"
const val PARAM_GITHUB_REF = "GITHUB_REF"
const val PARAM_GITHUB_SHA = "GITHUB_SHA"
/**
 * Environment variable should be set to the workspace directory that the Git repository is checked out in.
 * This is used to determine relative path to build files referenced in the dependency graph.
 */
const val PARAM_GITHUB_WORKSPACE = "GITHUB_WORKSPACE"

class GitHubSnapshotParams(private val pluginParameters: PluginParameters) {
    val dependencyGraphJobCorrelator: String = pluginParameters.load(PARAM_JOB_CORRELATOR)
    val dependencyGraphJobId: String =pluginParameters.load(PARAM_JOB_ID)
    val gitSha: String = pluginParameters.load(PARAM_GITHUB_SHA)
    val gitRef: String = pluginParameters.load(PARAM_GITHUB_REF)
    val gitHubWorkspace: Path = Paths.get(pluginParameters.load(PARAM_GITHUB_WORKSPACE))
}

