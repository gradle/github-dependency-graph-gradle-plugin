package org.gradle.github.dependencygraph

import org.gradle.dependencygraph.util.PluginParameters
import java.nio.file.Path
import java.nio.file.Paths

const val PARAM_JOB_ID = "GITHUB_DEPENDENCY_GRAPH_JOB_ID"
const val PARAM_JOB_CORRELATOR = "GITHUB_DEPENDENCY_GRAPH_JOB_CORRELATOR"
const val PARAM_GITHUB_REF = "GITHUB_DEPENDENCY_GRAPH_REF"
const val PARAM_GITHUB_SHA = "GITHUB_DEPENDENCY_GRAPH_SHA"
const val PARAM_GITHUB_DETECTOR_NAME = "GITHUB_DEPENDENCY_GRAPH_DETECTOR_NAME"
const val PARAM_GITHUB_DETECTOR_VERSION = "GITHUB_DEPENDENCY_GRAPH_DETECTOR_VERSION"
const val PARAM_GITHUB_DETECTOR_URL = "GITHUB_DEPENDENCY_GRAPH_DETECTOR_URL"
/**
 * Environment variable should be set to the workspace directory that the Git repository is checked out in.
 * This is used to determine relative path to build files referenced in the dependency graph.
 */
const val PARAM_GITHUB_WORKSPACE = "GITHUB_DEPENDENCY_GRAPH_WORKSPACE"

class GitHubSnapshotParams(pluginParameters: PluginParameters) {
    val dependencyGraphJobCorrelator: String = pluginParameters.load(PARAM_JOB_CORRELATOR)
    val dependencyGraphJobId: String = pluginParameters.load(PARAM_JOB_ID)
    val gitSha: String = pluginParameters.load(PARAM_GITHUB_SHA)
    val gitRef: String = pluginParameters.load(PARAM_GITHUB_REF)
    val gitHubWorkspace: Path = Paths.get(pluginParameters.load(PARAM_GITHUB_WORKSPACE))
    val githubDetectorName: String = pluginParameters.loadOptional(PARAM_GITHUB_DETECTOR_NAME)
                                   ?: javaClass.`package`.implementationTitle
    val githubDetectorVersion: String = pluginParameters.loadOptional(PARAM_GITHUB_DETECTOR_VERSION)
                                      ?: javaClass.`package`.implementationVersion
    val githubDetectorUrl: String = pluginParameters.loadOptional(PARAM_GITHUB_DETECTOR_URL)
                                  ?: "https://github.com/gradle/github-dependency-graph-gradle-plugin"
}

