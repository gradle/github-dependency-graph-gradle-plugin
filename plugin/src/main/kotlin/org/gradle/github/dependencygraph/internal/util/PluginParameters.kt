package org.gradle.github.dependencygraph.internal.util

private const val ENV_VIA_SYS_PROP_PREFIX = "org.gradle.github.env."

const val ENV_DEPENDENCY_GRAPH_JOB_ID = "GITHUB_DEPENDENCY_GRAPH_JOB_ID"
const val ENV_DEPENDENCY_GRAPH_JOB_CORRELATOR = "GITHUB_DEPENDENCY_GRAPH_JOB_CORRELATOR"
const val ENV_GITHUB_REF = "GITHUB_REF"
const val ENV_GITHUB_SHA = "GITHUB_SHA"

const val ENV_DEPENDENCY_GRAPH_REPORT_DIR = "GITHUB_DEPENDENCY_GRAPH_REPORT_DIR"

/**
 * Environment variable should be set to the workspace directory that the Git repository is checked out in.
 * This is used to determine relative path to build files referenced in the dependency graph.
 */
const val ENV_GITHUB_WORKSPACE = "GITHUB_WORKSPACE"

class PluginParameters {
    fun load(envName: String, default: String? = null): String {
        return System.getProperty(ENV_VIA_SYS_PROP_PREFIX + envName)
            ?: System.getenv()[envName]
            ?: default
            ?: throwEnvironmentVariableMissingException(envName)
    }

    private fun throwEnvironmentVariableMissingException(variable: String): Nothing {
        throw IllegalStateException("The configuration parameter '$variable' must be set, but it was not.")
    }
}