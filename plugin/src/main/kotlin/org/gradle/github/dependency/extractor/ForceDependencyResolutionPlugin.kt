package org.gradle.github.dependency.extractor

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle

@Suppress("unused")
class ForceDependencyResolutionPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        gradle.projectsEvaluated {
            val resolveAllDeps = gradle.rootProject.tasks.create("dependencyExtractor_resolveBuildDependencies")

            // Depend on "dependencies" task in all projects
            gradle.allprojects { project ->
                resolveAllDeps.dependsOn(project.tasks.named("dependencies"))
            }

            // Depend on all 'resolveBuildDependencies' task in each included build
            gradle.includedBuilds.forEach {
                resolveAllDeps.dependsOn(it.task(":dependencyExtractor_resolveBuildDependencies"))
            }

            // Set the default task
            gradle.rootProject.defaultTasks = listOf("dependencyExtractor_resolveBuildDependencies")
        }
    }
}
