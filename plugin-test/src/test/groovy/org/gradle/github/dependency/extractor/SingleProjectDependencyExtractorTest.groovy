package org.gradle.github.dependency.extractor

import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage

class SingleProjectDependencyExtractorTest extends BaseExtractorTest {
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

    private def singleProjectBuildWithBuildscript(@GroovyBuildScriptLanguage String dependenciesDeclaration) {
        singleProjectBuild("a") {
            buildFile """
            buildscript {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                $dependenciesDeclaration
            }
            apply plugin: 'java'
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
        def manifests = jsonManifests()
        def runtimeClasspathManifest = manifests["::runtimeClasspath"] as Map
        runtimeClasspathManifest.name == "::runtimeClasspath"
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
        def manifests = jsonManifests()
        def runtimeClasspathManifest = manifests["::runtimeClasspath"] as Map
        runtimeClasspathManifest.name == "::runtimeClasspath"
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
        def manifests = jsonManifests()
        def runtimeClasspathManifest = manifests["::runtimeClasspath"] as Map
        runtimeClasspathManifest.name == "::runtimeClasspath"
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

    def "build with one dependency and one transitive when multiple configurations are resolved"() {
        given:
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        mavenRepo.module("org.test", "foo", "1.0").dependsOn(bar).publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
        }
        """
        javaTestSourceFile """
        public class Test {}
        """
        when:
        succeeds("build")

        then:
        def manifests = jsonManifests()
        ["::compileClasspath", "::testCompileClasspath"].forEach {
            def classpathManifest = manifests[it] as Map
            classpathManifest.name == it
            def file = classpathManifest.file as Map
            file.source_location == "build.gradle.kts"
            def resolved = classpathManifest.resolved as Map
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

    def "build with dependency updated transitively"() {
        given:
        mavenRepo.module("org.test", "bar", "1.0").publish()
        def barNewer = mavenRepo.module("org.test", "bar", "1.1").publish()
        mavenRepo.module("org.test", "foo", "1.0").dependsOn(barNewer).publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:bar:1.0" // Direct dependency upon older version
            implementation "org.test:foo:1.0" // Transitive dependency upon newer version
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def manifests = jsonManifests()
        def runtimeClasspathManifest = manifests["::runtimeClasspath"] as Map
        runtimeClasspathManifest.name == "::runtimeClasspath"
        def file = runtimeClasspathManifest.file as Map
        file.source_location == "build.gradle.kts"
        def resolved = runtimeClasspathManifest.resolved as Map
        def testFoo = resolved["pkg:maven/org.test/foo@1.0"] as Map
        verifyAll(testFoo) {
            purl == "pkg:maven/org.test/foo@1.0"
            relationship == "direct"
            dependencies == ["pkg:maven/org.test/bar@1.1"]
        }
        def testBar = resolved["pkg:maven/org.test/bar@1.1"] as Map
        verifyAll(testBar) {
            purl == "pkg:maven/org.test/bar@1.1"
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with transitive dependency updated directly"() {
        given:
        def barOlder = mavenRepo.module("org.test", "bar", "1.0").publish()
        mavenRepo.module("org.test", "bar", "1.1").publish()
        mavenRepo.module("org.test", "foo", "1.0").dependsOn(barOlder).publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:bar:1.1" // Direct dependency upon newer version
            implementation "org.test:foo:1.0" // Transitive dependency upon older version
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def manifests = jsonManifests()
        def runtimeClasspathManifest = manifests["::runtimeClasspath"] as Map
        runtimeClasspathManifest.name == "::runtimeClasspath"
        def file = runtimeClasspathManifest.file as Map
        file.source_location == "build.gradle.kts"
        def resolved = runtimeClasspathManifest.resolved as Map
        def testFoo = resolved["pkg:maven/org.test/foo@1.0"] as Map
        verifyAll(testFoo) {
            purl == "pkg:maven/org.test/foo@1.0"
            relationship == "direct"
            dependencies == ["pkg:maven/org.test/bar@1.1"]
        }
        def testBarIndirect = resolved["pkg:maven/org.test/bar@1.1"] as Map
        verifyAll(testBarIndirect) {
            purl == "pkg:maven/org.test/bar@1.1"
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with buildscript dependencies"() {
        given:
        mavenRepo.module("org.test", "foo", "1.0").publish()
        singleProjectBuildWithBuildscript """
        dependencies {
            classpath "org.test:foo:1.0"
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def manifests = jsonManifests()
        def classpathManifest = manifests[":classpath"] as Map
        classpathManifest.name == ":classpath"
        def classpathResolved = classpathManifest.resolved as Map
        def testFoo = classpathResolved["pkg:maven/org.test/foo@1.0"] as Map
        verifyAll(testFoo) {
            purl == "pkg:maven/org.test/foo@1.0"
            relationship == "direct"
            dependencies == []
        }
        def runtimeClasspathManifest = manifests["::runtimeClasspath"] as Map
        runtimeClasspathManifest.name == "::runtimeClasspath"
        def runtimeClasspathResolved = runtimeClasspathManifest.resolved as Map
        runtimeClasspathResolved.isEmpty()
    }
}
