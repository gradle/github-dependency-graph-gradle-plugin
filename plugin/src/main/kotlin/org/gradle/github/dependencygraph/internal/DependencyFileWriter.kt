package org.gradle.github.dependencygraph.internal

import org.gradle.api.logging.Logging
import org.gradle.github.dependencygraph.internal.json.GitHubRepositorySnapshot
import java.io.File

class DependencyFileWriter(val manifestFile: File) {
    private var writtenFile: Boolean = false

    fun writeDependencySnapshot(graph: GitHubRepositorySnapshot): File {
        if (writtenFile) {
            return manifestFile
        }
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(JacksonJsonSerializer.serializeToJson(graph))
        writtenFile = true
        LOGGER.lifecycle("\nGitHubDependencyGraphPlugin: Wrote dependency snapshot to \n${manifestFile.canonicalPath}")
        return manifestFile
    }

    companion object {
        private val LOGGER = Logging.getLogger(DependencyFileWriter::class.java)
    }
}
