package org.gradle.github.dependency.extractor.internal

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.nio.file.Path

@Suppress("ClassName")
abstract class DependencyExtractorService_6_1 :
    DependencyExtractorService(),
    BuildService<DependencyExtractorService_6_1.Parameters> {

    abstract class Parameters : BuildServiceParameters {
        abstract val gitHubJobName: Property<String>
        abstract val gitHubRunNumber: Property<String>
        abstract val gitSha: Property<String>
        abstract val gitRef: Property<String>
        abstract val gitWorkspaceDirectory: DirectoryProperty
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
