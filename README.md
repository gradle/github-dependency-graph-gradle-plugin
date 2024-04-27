# GitHub Dependency Graph Gradle Plugin

A Gradle plugin for generating a GitHub dependency graph for a Gradle build, which can be uploaded to the [GitHub Dependency Submission API](https://docs.github.com/en/rest/dependency-graph/dependency-submission).

## Usage
This plugin is designed to be used in a GitHub Actions workflow, and is tightly integrated into the [Gradle Build Action](https://github.com/gradle/gradle-build-action#github-dependency-graph-support).

For other uses, the [core plugin](https://plugins.gradle.org/plugin/org.gradle.github-dependency-graph-gradle-plugin) (`org.gradle.github.GitHubDependencyGraphPlugin`) 
should be applied to the `Gradle` instance via a Gradle init script as follows:

```
import org.gradle.github.GitHubDependencyGraphPlugin
initscript {
  repositories {
    maven {
      url = uri("https://plugins.gradle.org/m2/")
    }
  }
  dependencies {
    classpath("org.gradle:github-dependency-graph-gradle-plugin:+")
  }
}
apply plugin: GitHubDependencyGraphPlugin
```

This causes 2 separate plugins to be applied, that can be used independently:
- `GitHubDependencyExtractorPlugin` collects all dependencies that are resolved during a build execution and writes these to a file. The output file can be found at `<root>/build/reports/github-depenency-graph-snapshots/<job-correlator>.json`.
- `ForceDependencyResolutionPlugin` creates a `ForceDependencyResolutionPlugin_resolveAllDependencies` task that will attempt to resolve all dependencies for a Gradle build, by simply invoking `dependencies` on all projects.

### Required environment variables

The following environment variables configure the snapshot generated by the `GitHubDependencyExtractorPlugin`. See the [GitHub Dependency Submission API docs](https://docs.github.com/en/rest/dependency-graph/dependency-submission?apiVersion=2022-11-28) for details:
- `GITHUB_DEPENDENCY_GRAPH_JOB_CORRELATOR`: Sets the `job.correlator` value for the dependency submission
- `GITHUB_DEPENDENCY_GRAPH_JOB_ID`: Sets the `job.id` value for the dependency submission
- `GITHUB_DEPENDENCY_GRAPH_REF`: Sets the `ref` value for the commit that generated the dependency graph
- `GITHUB_DEPENDENCY_GRAPH_SHA`: Sets the `sha` value for the commit that generated the dependency graph
- `GITHUB_DEPENDENCY_GRAPH_WORKSPACE`: Sets the root directory of the github repository. Must be an absolute path.
- `DEPENDENCY_GRAPH_REPORT_DIR` (optional): Specifies where the dependency graph report will be generated. Must be an absolute path.

Each of these values can also be provided via a system property. 
eg: Env var `DEPENDENCY_GRAPH_REPORT_DIR` can be set with `-DDEPENDENCY_GRAPH_REPORT_DIR=...` on the command-line.

### Filtering which Gradle Configurations contribute to the dependency graph

If you do not want to include every dependency configuration in every project in your build, you can limit the
dependency extraction to a subset of these.

The following parameters control the set of projects and configurations that contribute dependencies.
Each of these is a regular expression value, and can set either as an environment variable or as a system property on the command line.

| Property                                | Description               | Default                         |
|-----------------------------------------|---------------------------|---------------------------------|
| DEPENDENCY_GRAPH_INCLUDE_PROJECTS       | Projects to include       | All projects are included       |
| DEPENDENCY_GRAPH_EXCLUDE_PROJECTS       | Projects to exclude       | No projects are excluded        |
| DEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS | Configurations to include | All configurations are included |
| DEPENDENCY_GRAPH_EXCLUDE_CONFIGURATIONS | Configurations to exclude | No configurations are excluded  |

### Controlling the scope of dependencies in the dependency graph

The GitHub dependency graph allows a scope to be assigned to each reported dependency.
The only permissible values for scope are 'runtime' and 'development'.

The following parameters control the set of projects and configurations that provide 'runtime' scoped dependencies.
Any dependency resolution that does not match these parameters will be scoped 'development'.

Each of these parameters is a regular expression value, and can set either as an environment variable or as a system property on the command line.

| Property                                        | Description                                               | Default                         |
|-------------------------------------------------|-----------------------------------------------------------|---------------------------------|
| DEPENDENCY_GRAPH_RUNTIME_INCLUDE_PROJECTS       | Projects that can provide 'runtime' dependencies          | All projects are included       |
| DEPENDENCY_GRAPH_RUNTIME_EXCLUDE_PROJECTS       | Projects that do not provide 'runtime' dependencies       | No projects are excluded        |
| DEPENDENCY_GRAPH_RUNTIME_INCLUDE_CONFIGURATIONS | Configurations that contain 'runtime' dependencies        | All configurations are included |
| DEPENDENCY_GRAPH_RUNTIME_EXCLUDE_CONFIGURATIONS | Configurations that do not contain 'runtime' dependencies | No configurations are excluded  |

By default, no scope is assigned to dependencies in the graph. To enable scopes in the generated dependency graph,
at least one of these parameters must be configured.

For dependencies that are resolved in multiple projects and/or multiple configurations, only a single 'runtime' scoped resolution
is required for that dependency to be scoped 'runtime'.

### Gradle compatibility

The plugin should be compatible with most versions of Gradle >= 5.2, and has been tested against 
Gradle versions "5.2.1", "5.6.4", "6.0.1", "6.9.4", "7.1.1" and "7.6.3", as well as all patched versions of Gradle 8.x.

The plugin is compatible with running Gradle with the configuration-cache enabled: this support is
limited to Gradle "8.1.0" and later. Earlier Gradle versions will not work with `--configuration-cache`.
Note that no dependency graph will be generated when configuration state is loaded from the configuration-cache.

| Gradle version | Compatible | Compatible with configuration-cache |
| -------------- | ------- | ------------------------ |
| 1.x - 4.x      | :x: | :x: |
| 5.0 - 5.1.1 | :x: | :x: |
| 5.2 - 5.6.4 | ✅ | :x: |
| 6.0 - 6.9.4 | ✅ | :x: |
| 7.0 - 7.0.2 | :x: | :x: |
| 7.1 - 7.6.3 | ✅ | :x: |
| 8.0 - 8.0.2 | ✅ | :x: |
| 8.1+ | ✅ | ✅ |

### Dependency verification

When using this plugin with [dependency signature verification enabled](https://docs.gradle.org/current/userguide/dependency_verification.html#sec:signature-verification), 
the you should be able to update your `dependency-verification.xml` file using `--write-verification-metadata pgp,sha256`.

However, if this doesn't work, you can add the following to your `dependency-verificaton.xml` file:

```
<trusted-keys>
   <trusted-key id="7B79ADD11F8A779FE90FD3D0893A028475557671" group="org.gradle" name="github-dependency-graph-gradle-plugin"/>
</trusted-keys>
```

## Using the plugin to generate dependency reports

As well as the `GitHubDependencyGraphPlugin`, which is tailored for use by the [gradle/actions/dependency-submission](https://github.com/gradle/actions/tree/main/dependency-submission) GitHub Action, this repository also provides the `SimpleDependencyGraphPlugin`, which generates dependency-graph outputs in simple text format.

To use the `SimpleDependencyGraphPlugin` you'll need to create an `init-script.gradle` file to apply the plugin to your project:

```groovy
initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath "org.gradle:github-dependency-graph-gradle-plugin:+"
    }
}
apply plugin: org.gradle.dependencygraph.simple.SimpleDependencyGraphPlugin
```

and then execute the task to resolve all dependencies in your project:

```
./gradlew -I init.gradle --dependency-verification=off --no-configuration-cache --no-configure-on-demand :ForceDependencyResolutionPlugin_resolveAllDependencies
```

You'll find the generated files in `build/dependency-graph-snapshots`.

### Using dependency reports to determine the underlying source of a dependency

After generating the dependency reports as described, it is possible to determine the dependency source by:

1. Locate the dependency (including matching version) in the `dependency-resolution.json` file.
2. Inspect each `resolvedBy` entry for the `path` and `configuration` values. The `scope` value is unimportant in this context.
3. Use the built-in [dependencyInsight](https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#dependency_insights) task to determine exactly how the dependency was resolved. The `path` indicates the project where the task should be executed, and the `configuration` is an input to the task.

For example, given the following from the `dependency-resolution.json` report:
```
  "dependency" : "com.google.guava:guava:32.1.3-jre",
  "effectiveScope" : "Unknown",
  "resolvedBy" : [ {
    "path" : ":my-subproject",
    "configuration" : "compileClasspath",
    "scope" : "Unknown"
  }, ...
```

You would run the command:
```
./gradlew :my-subproject:dependencyInsight --configuration compileClasspath --dependency com.google.guava:guava:32.1.3-jre
```

#### Dealing with 'classpath' configuration

If the configuration value in `dependency-resolution.json` is "classpath", or for some other reason the above instructions do not work,
it is possible to recostruct the full resolution path using the generated `dependency-graph.json` file.

Search for the exact dependency version in `dependency-graph.json`, and you'll see an "id" entry for that dependency as well as one or more
"dependencies" entries. By tracing back through the dependencies you can determine the underlying source of the dependency.


## Building/Testing

To build and test this plugin, run the following task:
```shell
./gradlew check
```

To self-test this plugin and generate a dependency graph for this repository, run:
```shell
./plugin-self-test-local
```

The generated dependency graph will be submitted to GitHub only if you supply a
[GitHub API token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token)
via the environment variable `GITHUB_TOKEN`.

