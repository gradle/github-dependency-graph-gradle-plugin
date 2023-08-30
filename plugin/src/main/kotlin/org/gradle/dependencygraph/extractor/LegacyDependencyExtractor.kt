package org.gradle.dependencygraph.extractor

import org.gradle.initialization.EvaluateSettingsBuildOperationType

class LegacyDependencyExtractor(private val rendererClassName: String) : DependencyExtractor() {
    override fun getRendererClassName(): String {
        return rendererClassName
    }

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