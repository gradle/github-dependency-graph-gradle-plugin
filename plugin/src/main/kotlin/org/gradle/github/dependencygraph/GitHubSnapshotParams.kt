package org.gradle.github.dependencygraph

class GitHubSnapshotParams(
    val dependencyGraphJobCorrelator: String,
    val dependencyGraphJobId: String,
    val gitSha: String,
    val gitRef: String)