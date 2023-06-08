package org.gradle.github.dependencygraph.internal

import org.gradle.api.logging.Logging
import org.gradle.github.dependencygraph.internal.json.GitHubRepositorySnapshot
import java.io.File

class DependencyFileWriter
private constructor(
    private val manifestFile: File,
    private val loggerWarning: () -> Unit
) {

    private var writtenFile: Boolean = false

    fun writeDependencyManifest(graph: GitHubRepositorySnapshot): File {
        if (writtenFile) {
            return manifestFile
        }
        loggerWarning()
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(JacksonJsonSerializer.serializeToJson(graph))
        writtenFile = true
        return manifestFile
    }

    companion object {
        private val LOGGER = Logging.getLogger(DependencyFileWriter::class.java)

        fun create(buildDirectory: File): DependencyFileWriter =
            create(buildDirectory) {
                // No-op
            }

        fun create(): DependencyFileWriter =
            create(File(".")) {
                LOGGER.warn(
                    "[WARNING] Something went wrong configuring the GithubDependencyExtractorPlugin. " +
                        "Using JVM working directory as root of build"
                )
            }

        private fun create(buildDirectory: File, loggerWarning: () -> Unit): DependencyFileWriter {
            return DependencyFileWriter(
                File(
                    buildDirectory,
                    "reports/github-dependency-report/github-dependency-manifest.json"
                ),
                loggerWarning
            )
        }
    }
}
