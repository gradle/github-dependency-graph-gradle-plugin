package org.gradle.github.dependencygraph.internal.github.json

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

data class GitHubRepositorySnapshot(
    val version: Int = 0,
    val job: GitHubJob,
    val sha: String,
    val ref: String,
    val detector: GitHubDetector,
    val manifests: Map<String, GitHubManifest>,
    val scanned: String = scannedTime()
) {
    companion object {
        private fun scannedTime(): String {
            val tz = TimeZone.getTimeZone("UTC")
            val df: DateFormat =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            df.timeZone = tz
            return df.format(Date())
        }
    }
}
