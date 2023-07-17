package org.gradle.github.forceresolve

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

private const val RESOLVE_TASK = "GitHubDependencyGraphPlugin_resolveProjectDependencies"
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
                val projectTaskFactory = getResolveProjectDependenciesTaskFactory()
                val resolveProjectDeps = projectTaskFactory.create(project)
                resolveAllDeps.configure {
                    it.dependsOn(resolveProjectDeps)
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

    private fun getResolveProjectDependenciesTaskFactory(): ResolveProjectDependenciesTaskFactory {
        val gradleVersion = GradleVersion.current()
        val gradle8 = GradleVersion.version("8.0")
        if (gradleVersion >= gradle8) {
            return ResolveProjectDependenciesTaskFactory.Current
        } else {
            return ResolveProjectDependenciesTaskFactory.Legacy
        }
    }

    private interface ResolveProjectDependenciesTaskFactory {
        fun create(project: Project): TaskProvider<out Task>

        object Current : ResolveProjectDependenciesTaskFactory {
            override fun create(project: Project): TaskProvider<out Task> {
                return project.tasks.register(RESOLVE_TASK, ResolveProjectDependenciesTask::class.java)
            }
        }

        object Legacy : ResolveProjectDependenciesTaskFactory {
            override fun create(project: Project): TaskProvider<out Task> {
                return project.tasks.register(RESOLVE_TASK, LegacyResolveProjectDependenciesTask::class.java)
            }
        }
    }

}