package org.gradle.github.dependency.util

import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

internal interface EnvironmentVariableLoader {

    interface Default {
        fun Gradle.loadEnvironmentVariable(envName: String, default: String? = null): Provider<String> =
            service<ProviderFactory>().run {
                environmentVariable(envName)
                    .orElse(systemProperty(ENV_VIA_SYS_PROP_PREFIX + envName))
                    .orElse(provider {
                        default ?: throwEnvironmentVariableMissingException(envName)
                    })
            }
    }

    interface Legacy {
        fun Gradle.loadEnvironmentVariable(envName: String, default: String? = null): String {
            return System.getenv()[envName]
                ?: System.getProperty(ENV_VIA_SYS_PROP_PREFIX + envName)
                ?: default
                ?: throwEnvironmentVariableMissingException(envName)
        }
    }
}

/**
 * Allows environment variable values to be set via command-line system properties.
 */
private const val ENV_VIA_SYS_PROP_PREFIX = "org.gradle.github.env."

internal fun throwEnvironmentVariableMissingException(variable: String): Nothing {
    throw IllegalStateException("The environment variable '$variable' must be set, but it was not.")
}
