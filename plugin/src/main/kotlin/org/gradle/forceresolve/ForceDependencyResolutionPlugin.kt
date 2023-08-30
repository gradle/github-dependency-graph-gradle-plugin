package org.gradle.forceresolve

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

// TODO: Rename these
private const val RESOLVE_PROJECT_TASK = "ForceDependencyResolutionPlugin_resolveProjectDependencies"
private const val RESOLVE_ALL_TASK = "ForceDependencyResolutionPlugin_resolveAllDependencies"

/**
 * Adds a task to resolve all dependencies in a Gradle build tree.
 */
class ForceDependencyResolutionPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        gradle.projectsEvaluated {
            val resolveAllDeps = gradle.rootProject.tasks.register(RESOLVE_ALL_TASK)

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
                    it.dependsOn(includedBuild.task(":$RESOLVE_ALL_TASK"))
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
                return project.tasks.register(RESOLVE_PROJECT_TASK, ResolveProjectDependenciesTask::class.java)
            }
        }

        object Legacy : ResolveProjectDependenciesTaskFactory {
            override fun create(project: Project): TaskProvider<out Task> {
                return project.tasks.register(RESOLVE_PROJECT_TASK, LegacyResolveProjectDependenciesTask::class.java)
            }
        }
    }

}