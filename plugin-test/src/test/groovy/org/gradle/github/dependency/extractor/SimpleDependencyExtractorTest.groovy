package org.gradle.github.dependency.extractor

class SimpleDependencyExtractorTest extends BaseExtractorTest {
    def setup() {
        applyExtractorPlugin()
    }

    def "build with single dependency"() {
        given:
        mavenRepo.module("org.test", "foo", "1.0").publish()
        singleProjectBuild("a") {
            buildFile """
            apply plugin: 'java'

            repositories {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
            dependencies {
                implementation "org.test:foo:1.0"
            }
            """
        }
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def manifest = jsonManifest()
        manifest instanceof Map
        manifest.manifests instanceof Map
        def manifests = manifest.manifests as Map
        def runtimeClasspathManifest = manifests[":runtimeClasspath"]
        runtimeClasspathManifest instanceof Map
        runtimeClasspathManifest.name == ":runtimeClasspath"
        def file = runtimeClasspathManifest.file
        file instanceof Map
        file.source_location == "build.gradle.kts"
        def resolved = runtimeClasspathManifest.resolved
        resolved instanceof Map
        def junit = resolved["pkg:maven/org.test/foo@1.0"]
        junit instanceof Map
        junit.purl == "pkg:maven/org.test/foo@1.0"
        junit.relationship == "direct"
        junit.dependencies == []
    }
}
