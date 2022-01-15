package org.gradle.github.dependency.uploader.internal

import spock.lang.Specification
import spock.lang.Subject

class DependencyUploaderServiceTest extends Specification {
    @Subject
    def dependencyUploaderService = new DependencyUploaderService() {

        @Override
        String getGitHubAPIUrl() {
            return "https://api.github.com"
        }

        @Override
        String getGitHubRepository() {
            return "gradle/github-dependency-extractor"
        }

        @Override
        String getGitHubToken() {
            return ""
        }
    }

    def "can create upload uri from provided variables"() {
        expect:
        dependencyUploaderService.requestUrl() == new URI(
                "https://api.github.com/repos/gradle/github-dependency-extractor/snapshots"
        )
    }
}
