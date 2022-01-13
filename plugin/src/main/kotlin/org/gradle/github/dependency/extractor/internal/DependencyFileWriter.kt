package org.gradle.github.dependency.extractor.internal

import org.gradle.github.dependency.extractor.internal.json.GitHubDependencyGraph
import org.slf4j.LoggerFactory
import java.io.File

class DependencyFileWriter
private constructor(
    private val manifestFile: File,
    private val loggerWarning: () -> Unit
) {

    fun writeDependencyManifest(graph: GitHubDependencyGraph) {
        loggerWarning()
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(JacksonJsonSerializer.serializeToJson(graph))
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DependencyFileWriter::class.java)

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
