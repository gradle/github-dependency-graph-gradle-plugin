package org.gradle.dependencygraph.util


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