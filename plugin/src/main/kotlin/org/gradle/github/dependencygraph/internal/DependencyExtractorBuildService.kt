package org.gradle.github.dependencygraph.internal

import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class DependencyExtractorBuildService :
    DependencyExtractor(),
    BuildService<DependencyExtractorBuildService.Params>
{
    // Some parameters for the web server
    internal interface Params : BuildServiceParameters {
        val rendererClassName: Property<String>
    }

    override fun getRendererClassName(): String {
        return parameters.rendererClassName.get()
    }
}