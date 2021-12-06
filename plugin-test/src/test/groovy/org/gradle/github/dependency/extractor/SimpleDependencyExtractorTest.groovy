package org.gradle.github.dependency.extractor

import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage

class SimpleDependencyExtractorTest extends BaseExtractorTest {
    def setup() {
        applyExtractorPlugin()
    }

    private def singleProjectBuildWithDependencies(@GroovyBuildScriptLanguage String dependenciesDeclaration) {
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
        mavenRepo.module("org.test", "foo", "1.0").publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def manifest = jsonManifest() as Map
        def manifests = manifest.manifests as Map
        def runtimeClasspathManifest = manifests[":runtimeClasspath"] as Map
        runtimeClasspathManifest.name == ":runtimeClasspath"
        def file = runtimeClasspathManifest.file as Map
        file.source_location == "build.gradle.kts"
        def resolved = runtimeClasspathManifest.resolved as Map
        def testFoo = resolved["pkg:maven/org.test/foo@1.0"]
        testFoo instanceof Map
        verifyAll(testFoo as Map) {
            purl == "pkg:maven/org.test/foo@1.0"
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with two dependencies"() {
        given:
        mavenRepo.module("org.test", "foo", "1.0").publish()
        mavenRepo.module("org.test", "bar", "1.0").publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
            implementation "org.test:bar:1.0"
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def manifest = jsonManifest() as Map
        def manifests = manifest.manifests as Map
        def runtimeClasspathManifest = manifests[":runtimeClasspath"] as Map
        runtimeClasspathManifest.name == ":runtimeClasspath"
        def file = runtimeClasspathManifest.file as Map
        file.source_location == "build.gradle.kts"
        def resolved = runtimeClasspathManifest.resolved as Map
        def testFoo = resolved["pkg:maven/org.test/foo@1.0"] as Map
        verifyAll(testFoo) {
            purl == "pkg:maven/org.test/foo@1.0"
            relationship == "direct"
            dependencies == []
        }
        def testBar = resolved["pkg:maven/org.test/bar@1.0"] as Map
        verifyAll(testBar) {
            purl == "pkg:maven/org.test/bar@1.0"
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with one dependency and one transitive"() {
        given:
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        mavenRepo.module("org.test", "foo", "1.0").dependsOn(bar).publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def manifest = jsonManifest() as Map
        def manifests = manifest.manifests as Map
        def runtimeClasspathManifest = manifests[":runtimeClasspath"] as Map
        runtimeClasspathManifest.name == ":runtimeClasspath"
        def file = runtimeClasspathManifest.file as Map
        file.source_location == "build.gradle.kts"
        def resolved = runtimeClasspathManifest.resolved as Map
        def testFoo = resolved["pkg:maven/org.test/foo@1.0"] as Map
        verifyAll(testFoo) {
            purl == "pkg:maven/org.test/foo@1.0"
            relationship == "direct"
            dependencies == ["pkg:maven/org.test/bar@1.0"]
        }
        def testBar = resolved["pkg:maven/org.test/bar@1.0"] as Map
        verifyAll(testBar) {
            purl == "pkg:maven/org.test/bar@1.0"
            relationship == "indirect"
            dependencies == []
        }
    }
}
