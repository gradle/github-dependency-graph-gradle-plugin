package org.gradle.github.dependencygraph

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle

private const val GENERATE_TASK = "GitHubDependencyGraphPlugin_generateDependencyGraph"

/**
 * Adds a task to resolve all dependencies in a Gradle build tree.
 */
class ForceDependencyResolutionPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        gradle.projectsEvaluated {
            val resolveAllDeps = gradle.rootProject.tasks.register(GENERATE_TASK)

            // Depend on "dependencies" task in all projects
            gradle.allprojects { project ->
                resolveAllDeps.configure {
                    it.dependsOn(project.tasks.named("dependencies"))
                }
            }

            // Depend on all 'resolveBuildDependencies' task in each included build
            gradle.includedBuilds.forEach { includedBuild ->
                resolveAllDeps.configure {
                    it.dependsOn(includedBuild.task(":$GENERATE_TASK"))
                }
            }
        }
    }
}
