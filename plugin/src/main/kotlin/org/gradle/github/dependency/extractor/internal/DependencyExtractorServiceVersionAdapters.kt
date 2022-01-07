package org.gradle.github.dependency.extractor.internal

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.nio.file.Path

@Suppress("ClassName")
abstract class DependencyExtractorService_6_1 :
    DependencyExtractorService(),
    BuildService<DependencyExtractorService_6_1.Parameters> {

    abstract class Parameters : BuildServiceParameters {
        abstract val gitWorkspaceDirectory: DirectoryProperty
    }

    override val gitWorkspaceDirectory: Path
        get() = parameters.gitWorkspaceDirectory.get().asFile.toPath()
}
