package org.gradle.github.dependencygraph.internal

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class DependencyExtractorBuildService :
    DependencyExtractor(),
    BuildService<BuildServiceParameters.None>
