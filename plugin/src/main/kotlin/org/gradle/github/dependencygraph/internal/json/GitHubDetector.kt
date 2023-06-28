package org.gradle.github.dependencygraph.internal.json

data class GitHubDetector(
    val name: String = GitHubDetector::class.java.`package`.implementationTitle,
    val version: String = GitHubDetector::class.java.`package`.implementationVersion,
    val url: String = "https://github.com/gradle/github-dependency-graph-gradle-plugin"
)
