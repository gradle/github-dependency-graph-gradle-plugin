package org.gradle.github.dependency.extractor

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle

/**
 * Adds a task to resolve all dependencies in a Gradle build tree.
 */
class ForceDependencyResolutionPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        gradle.projectsEvaluated {
            val resolveAllDeps = gradle.rootProject.tasks.register("dependencyExtractor_resolveAllDependencies")

            // Depend on "dependencies" task in all projects
            gradle.allprojects { project ->
                resolveAllDeps.configure {
                    it.dependsOn(project.tasks.named("dependencies"))
                }
            }

            // Depend on all 'resolveBuildDependencies' task in each included build
            gradle.includedBuilds.forEach { includedBuild ->
                resolveAllDeps.configure {
                    it.dependsOn(includedBuild.task(":dependencyExtractor_resolveAllDependencies"))
                }
            }
        }
    }
}
