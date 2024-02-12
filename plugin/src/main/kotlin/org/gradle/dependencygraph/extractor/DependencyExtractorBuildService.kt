package org.gradle.dependencygraph.extractor

import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent

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

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        super.finished(buildOperation, finishEvent)

        // Track build completion without 'buildFinished'
        if (buildOperation.metadata == BuildOperationCategory.RUN_WORK) {
            handleBuildCompletion(finishEvent.failure)
        }
    }
}