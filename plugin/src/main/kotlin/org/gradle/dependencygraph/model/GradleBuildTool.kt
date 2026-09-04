package org.gradle.dependencygraph.model

/**
 * The coordinates that GitHub advisories for the Gradle Build Tool are published against.
 */
const val GRADLE_BUILD_TOOL_GROUP = "org.gradle"
const val GRADLE_BUILD_TOOL_MODULE = "gradle-core"

fun gradleBuildToolId(gradleVersion: String) = "$GRADLE_BUILD_TOOL_GROUP:$GRADLE_BUILD_TOOL_MODULE:$gradleVersion"
