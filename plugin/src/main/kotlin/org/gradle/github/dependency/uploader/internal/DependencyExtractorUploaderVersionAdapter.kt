package org.gradle.github.dependency.uploader.internal

import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

@Suppress("ClassName")
abstract class DependencyExtractorUploaderService_6_1 :
    DependencyUploaderService(),
    BuildService<DependencyExtractorUploaderService_6_1.Parameters> {

    interface Parameters : BuildServiceParameters {
        val gitHubApiUrl: Property<String>
        val gitHubRepository: Property<String>
        val gitHubToken: Property<String>
    }

    override val gitHubAPIUrl: String
        get() = parameters.gitHubApiUrl.get()
    override val gitHubRepository: String
        get() = parameters.gitHubRepository.get()
    override val gitHubToken: String
        get() = parameters.gitHubToken.get()
}
