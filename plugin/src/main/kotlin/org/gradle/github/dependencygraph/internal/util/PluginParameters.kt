package org.gradle.github.dependencygraph.internal.util

// TODO work out the best place for different constants
const val PARAM_JOB_ID = "GITHUB_JOB_ID"
const val PARAM_JOB_CORRELATOR = "GITHUB_JOB_CORRELATOR"
const val PARAM_GITHUB_REF = "GITHUB_REF"
const val PARAM_GITHUB_SHA = "GITHUB_SHA"

const val PARAM_INCLUDE_PROJECTS = "DEPENDENCY_GRAPH_INCLUDE_PROJECTS"
const val PARAM_INCLUDE_CONFIGURATIONS = "DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS"

const val PARAM_REPORT_DIR = "DEPENDENCY_GRAPH_REPORT_DIR"

/**
 * Environment variable should be set to the workspace directory that the Git repository is checked out in.
 * This is used to determine relative path to build files referenced in the dependency graph.
 */
const val PARAM_GITHUB_WORKSPACE = "GITHUB_WORKSPACE"

class PluginParameters {
    fun load(envName: String, default: String? = null): String {
        return System.getProperty(envName)
            ?: System.getenv()[envName]
            ?: default
            ?: throwEnvironmentVariableMissingException(envName)
    }

    fun loadOptional(envName: String): String? {
        return System.getProperty(envName)
            ?: System.getenv()[envName]
    }

    private fun throwEnvironmentVariableMissingException(variable: String): Nothing {
        throw IllegalStateException("The configuration parameter '$variable' must be set: " +
            "set an environment variable, or use '-D${variable}=value' on the command-line.")
    }
}