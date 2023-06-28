# GitHub Dependency Graph Gradle Plugin

A Gradle plugin for generating a GitHub dependency graph for a Gradle build, which can be uploaded to the [GitHub Dependency Submission API](https://docs.github.com/en/rest/dependency-graph/dependency-submission).

## Usage
This plugin is designed to be used in a GitHub Actions workflow, with support coming in a future release of the [Gradle Build Action](https://github.com/gradle/gradle-build-action).

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
- `GitHubDependencyExtractorPlugin` collects all dependencies that are resolved during a build execution and writes these to a file. The output file can be found at `<root>/build/reports/github-depenency-graph-gradle-plugin/github-dependency-snapshot.json`.
- `ForceDependencyResolutionPlugin` creates a `GitHubDependencyGraphPlugin_generateDependencyGraph` task that will attempt to resolve all dependencies for a Gradle build, by simply invoking `dependencies` on all projects.

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
