package org.gradle.github.dependency.util

import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

internal interface EnvironmentVariableLoader {

    interface Default {
        fun Gradle.loadEnvironmentVariable(envName: String, default: String? = null): Provider<String> =
            service<ProviderFactory>().run {
                environmentVariable(envName)
                    .orElse(gradleProperty(ENV_VIA_PARAMETER_PREFIX + envName))
                    .orElse(provider {
                        default ?: throwEnvironmentVariableMissingException(envName)
                    })
            }
    }

    interface Legacy {
        fun Gradle.loadEnvironmentVariable(envName: String, default: String? = null): String {
            return System.getenv()[envName]
                ?: startParameter.projectProperties[ENV_VIA_PARAMETER_PREFIX + envName]
                ?: default
                ?: throwEnvironmentVariableMissingException(envName)
        }
    }
}

/**
 * Allows reading the environment variables from parameters.
 * This is here purely for debugging purposes.
 * The tooling API doesn't allow environment variables to be read when running in debug mode.
 */
private const val ENV_VIA_PARAMETER_PREFIX = "org.gradle.github.internal.debug.env."

internal fun throwEnvironmentVariableMissingException(variable: String): Nothing {
    throw IllegalStateException("The environment variable '$variable' must be set, but it was not.")
}
