package org.gradle.github.dependency.extractor.internal

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.nio.file.Path

@Suppress("ClassName")
abstract class DependencyExtractorService_6_1 :
    DependencyExtractorService(),
    BuildService<DependencyExtractorService_6_1.Parameters> {

    interface Parameters : BuildServiceParameters {
        val gitHubJobName: Property<String>
        val gitHubRunNumber: Property<String>
        val gitSha: Property<String>
        val gitRef: Property<String>
        val gitWorkspaceDirectory: DirectoryProperty
    }

    override val gitHubJobName: String
        get() = parameters.gitHubJobName.get()
    override val gitHubRunNumber: String
        get() = parameters.gitHubRunNumber.get()
    override val gitSha: String
        get() = parameters.gitSha.get()
    override val gitRef: String
        get() = parameters.gitRef.get()
    override val gitWorkspaceDirectory: Path
        get() = parameters.gitWorkspaceDirectory.get().asFile.toPath()
}
