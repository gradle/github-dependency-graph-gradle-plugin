package org.gradle.github.dependency.uploader

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.github.dependency.extractor.GithubDependencyExtractorPlugin

@Suppress("unused")
class GithubDependencyUploaderPlugin: Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        gradle.plugins.apply(GithubDependencyExtractorPlugin::class.java)
    }
}
