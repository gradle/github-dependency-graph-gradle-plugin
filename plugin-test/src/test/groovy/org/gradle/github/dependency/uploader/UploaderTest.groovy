package org.gradle.github.dependency.uploader

import spock.lang.Ignore

import static com.github.tomakehurst.wiremock.client.WireMock.*

@Ignore("Not currently passing")
class UploaderTest extends BaseUploaderTest {

    def setup() {
        applyExtractorPlugin()
        file("init.gradle") << """
        apply plugin: org.gradle.github.dependency.uploader.GithubDependencyUploaderPlugin
        """
        establishEnvironmentVariables()
    }

    private def singleProjectBuildWithDependencies(String dependenciesDeclaration) {
        singleProjectBuild("a") {
            buildFile """
            apply plugin: 'java'

            repositories {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            $dependenciesDeclaration
            """
        }
    }

    def "build with single dependency"() {
        given:
        def foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        def fooPurl = purlFor(foo)
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
        }
        """
        and:

        stubFor(
                post(urlPathEqualTo("/repos/${environmentVars.gitHubRepository}/snapshots"))
                        .withHeader("Authorization", equalTo("Bearer ${environmentVars.gitHubToken}"))
                        .withHeader("Content-Type", matching("^application/json;.*"))
                        .willReturn(
                                okJson("{ \"id\": 3584622, \"created_at\": \"2022-01-14T08:49:12.380-08:00\" }")
                                        .withHeader("x-github-request-id", UUID.randomUUID().toString()))
        )

        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def runtimeClasspathManifest = jsonRepositorySnapshot(configuration: "runtimeClasspath")
        def file = runtimeClasspathManifest.file as Map
        file.source_location == "build.gradle"
        def resolved = runtimeClasspathManifest.resolved as Map
        def testFoo = resolved[fooPurl]
        testFoo instanceof Map
        verifyAll(testFoo as Map) {
            purl == fooPurl
            relationship == "direct"
            dependencies == []
        }
    }
}
