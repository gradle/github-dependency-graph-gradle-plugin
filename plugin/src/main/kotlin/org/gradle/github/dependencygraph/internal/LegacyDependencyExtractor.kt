package org.gradle.github.dependencygraph.internal

import org.gradle.initialization.EvaluateSettingsBuildOperationType

class LegacyDependencyExtractor : DependencyExtractor() {
    override fun extractSettings(details: EvaluateSettingsBuildOperationType.Details) {
        // Extraction fails for included builds on Gradle 5.x.
        // It's OK to ignore these events since we only care about the root build settings file at this stage.
        try {
            super.extractSettings(details)
        } catch (e: IllegalStateException) {
            // Ignore
            println("Failed to load path for included build with settings: ${details.settingsFile}")
        }
    }
}