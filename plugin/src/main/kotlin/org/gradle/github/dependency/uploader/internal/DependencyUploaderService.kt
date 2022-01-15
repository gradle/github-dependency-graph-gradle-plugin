package org.gradle.github.dependency.uploader.internal

import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.FileEntity
import org.apache.hc.core5.net.URIBuilder
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.github.dependency.extractor.internal.DependencyExtractorService
import org.gradle.github.dependency.uploader.GithubDependencyUploaderPlugin.Companion.ENV_GITHUB_REPOSITORY
import java.io.File
import java.net.URI

abstract class DependencyUploaderService: AutoCloseable {
    companion object {
        private val LOGGER = Logging.getLogger(DependencyUploaderService::class.java)
    }

    internal lateinit var dependencyExtractorServiceProvider: Provider<out DependencyExtractorService>

    abstract val gitHubAPIUrl: String
    abstract val gitHubRepository: String
    abstract val gitHubToken: String

    private fun throwIllegalRepositoryException(): Nothing =
        throw IllegalArgumentException("$ENV_GITHUB_REPOSITORY must be in the format 'owner/repository'")

    fun requestUrl(): URI {
        val gitHubRepositorySplit = gitHubRepository.split("/")
        if (gitHubRepositorySplit.size != 2) {
            throwIllegalRepositoryException()
        }
        val gitHubRepositoryOwner = gitHubRepositorySplit[0]
        val gitHubRepositoryName = gitHubRepositorySplit[1]
        if (gitHubRepositoryOwner.isEmpty() || gitHubRepositoryName.isEmpty()) {
            throwIllegalRepositoryException()
        }
        return URIBuilder(gitHubAPIUrl)
            .appendPathSegments(
                "repos",
                gitHubRepositoryOwner,
                gitHubRepositoryName,
                "snapshots"
            )
            .build()
    }

    private fun createHttpClient() =
        HttpClientBuilder
            .create()
            .useSystemProperties()
            .build()

    private fun doUpload(file: File) {
        createHttpClient().use { httpClient ->
            val requestUrl = requestUrl()
            val httpPost = HttpPost(requestUrl)
            httpPost.setHeader("Authorization", "Bearer $gitHubToken")
            val fileEntity = FileEntity(file, ContentType.APPLICATION_JSON)
            httpPost.entity = fileEntity
            LOGGER.lifecycle("Uploading GitHub Repository Snapshot to $requestUrl")
            httpClient.execute(httpPost).use { response ->
                val responseHeaderValue: String? =
                    response.getFirstHeader("x-github-request-id")?.value
                val responseData = EntityUtils.toString(response.entity)
                val lifecycleLog = buildString {
                    val newline = System.lineSeparator()
                    append("Uploaded GitHub Repository Snapshot:").append(newline)
                    append("\tResponse: ").append(responseData).append(newline)
                    append("\tResponse Header: x-github-request-id: ").append(responseHeaderValue).append(newline)
                }
                LOGGER.lifecycle(lifecycleLog)
            }
        }
    }

    override fun close() {
        doUpload(dependencyExtractorServiceProvider.get().writeAndGetSnapshotFile())
    }

}
