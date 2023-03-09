package org.gradle.github.dependency.extractor

import org.gradle.github.dependency.base.BaseExtractorTest

class SingleProjectDependencyExtractorTest extends BaseExtractorTest {
    def setup() {
        applyExtractorPlugin()
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

    private def singleProjectBuildWithBuildscript(String dependenciesDeclaration) {
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
        def foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def runtimeClasspathManifest = jsonRepositorySnapshot(configuration: "runtimeClasspath")
        def file = runtimeClasspathManifest.file as Map
        file.source_location == "build.gradle"
        def resolved = runtimeClasspathManifest.resolved as Map
        def testFoo = resolved[gavFor(foo)]
        verifyAll(testFoo as Map) {
            purl == purlFor(foo)
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with single dependency compiled & built"() {
        given:
        def foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
        }
        """
        when:
        succeeds("build")

        then:
        def runtimeClasspathManifest = jsonRepositorySnapshot(configuration: "compileClasspath")
        verifyAll {
            def file = runtimeClasspathManifest.file as Map
            file.source_location == "build.gradle"
            def resolved = runtimeClasspathManifest.resolved as Map
            def testFoo = resolved[gavFor(foo)]
            verifyAll(testFoo as Map) {
                purl == purlFor(foo)
                relationship == "direct"
                dependencies == []
            }
        }
        def annotationProcessorManifest = jsonRepositorySnapshot(configuration: "annotationProcessor")
        verifyAll {
            def file = annotationProcessorManifest.file as Map
            file.source_location == "build.gradle"
            def resolved = annotationProcessorManifest.resolved as Map
            resolved.isEmpty()
        }
    }

    def "build with two dependencies"() {
        given:
        def foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
            implementation "org.test:bar:1.0"
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def runtimeClasspathManifest = jsonRepositorySnapshot(configuration: "runtimeClasspath")
        def file = runtimeClasspathManifest.file as Map
        file.source_location == "build.gradle"
        def resolved = runtimeClasspathManifest.resolved as Map
        def testFoo = resolved[gavFor(foo)] as Map
        verifyAll(testFoo) {
            purl == purlFor(foo)
            relationship == "direct"
            dependencies == []
        }
        def testBar = resolved[gavFor(bar)] as Map
        verifyAll(testBar) {
            purl == purlFor(bar)
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with one dependency and one transitive"() {
        given:
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(bar).publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def runtimeClasspathManifest = jsonRepositorySnapshot(configuration: "runtimeClasspath")
        def file = runtimeClasspathManifest.file as Map
        file.source_location == "build.gradle"
        def resolved = runtimeClasspathManifest.resolved as Map
        def testFoo = resolved[gavFor(foo)] as Map
        verifyAll(testFoo) {
            purl == purlFor(foo)
            relationship == "direct"
            dependencies == [gavFor(bar)]
        }
        def testBar = resolved[gavFor(bar)] as Map
        verifyAll(testBar) {
            purl == purlFor(bar)
            relationship == "indirect"
            dependencies == []
        }
    }

    def "build with one dependency and one transitive when multiple configurations are resolved"() {
        given:
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(bar).publish()
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
        ["compileClasspath", "testCompileClasspath"].forEach {
            def classpathManifest = jsonRepositorySnapshot(configuration: it)
            def file = classpathManifest.file as Map
            file.source_location == "build.gradle"
            def resolved = classpathManifest.resolved as Map
            def testFoo = resolved[gavFor(foo)] as Map
            verifyAll(testFoo) {
                purl == purlFor(foo)
                relationship == "direct"
                dependencies == [gavFor(bar)]
            }
            def testBar = resolved[gavFor(bar)] as Map
            verifyAll(testBar) {
                purl == purlFor(bar)
                relationship == "indirect"
                dependencies == []
            }
        }
    }

    def "build with dependency updated transitively"() {
        given:
        mavenRepo.module("org.test", "bar", "1.0").publish()
        def barNewer = mavenRepo.module("org.test", "bar", "1.1").publish()
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(barNewer).publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:bar:1.0" // Direct dependency upon older version
            implementation "org.test:foo:1.0" // Transitive dependency upon newer version
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def runtimeClasspathManifest = jsonRepositorySnapshot(configuration: "runtimeClasspath")
        def file = runtimeClasspathManifest.file as Map
        file.source_location == "build.gradle"
        def resolved = runtimeClasspathManifest.resolved as Map
        def testFoo = resolved[gavFor(foo)] as Map
        verifyAll(testFoo) {
            purl == purlFor(foo)
            relationship == "direct"
            dependencies == [gavFor(barNewer)]
        }
        def testBar = resolved[gavFor(barNewer)] as Map
        verifyAll(testBar) {
            purl == purlFor(barNewer)
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with transitive dependency updated directly"() {
        given:
        def barOlder = mavenRepo.module("org.test", "bar", "1.0").publish()
        def bar = mavenRepo.module("org.test", "bar", "1.1").publish()
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(barOlder).publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:bar:1.1" // Direct dependency upon newer version
            implementation "org.test:foo:1.0" // Transitive dependency upon older version
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def runtimeClasspathManifest = jsonRepositorySnapshot(configuration: "runtimeClasspath")
        def file = runtimeClasspathManifest.file as Map
        file.source_location == "build.gradle"
        def resolved = runtimeClasspathManifest.resolved as Map
        def testFoo = resolved[gavFor(foo)] as Map
        verifyAll(testFoo) {
            purl == purlFor(foo)
            relationship == "direct"
            dependencies == [gavFor(bar)]
        }
        def testBarIndirect = resolved[gavFor(bar)] as Map
        verifyAll(testBarIndirect) {
            purl == purlFor(bar)
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with buildscript dependencies"() {
        given:
        def foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        singleProjectBuildWithBuildscript """
        dependencies {
            classpath "org.test:foo:1.0"
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def classpathManifest = jsonRepositorySnapshot(configuration: "classpath")
        def buildScriptFile = classpathManifest.file as Map
        buildScriptFile.source_location == "build.gradle"
        def classpathResolved = classpathManifest.resolved as Map
        def testFoo = classpathResolved[gavFor(foo)] as Map
        verifyAll(testFoo) {
            purl == purlFor(foo)
            relationship == "direct"
            dependencies == []
        }
        def runtimeClasspathManifest = jsonRepositorySnapshot(configuration: "runtimeClasspath")
        def runtimeClasspathFile = runtimeClasspathManifest.file as Map
        runtimeClasspathFile.source_location == "build.gradle"
        def runtimeClasspathResolved = runtimeClasspathManifest.resolved as Map
        runtimeClasspathResolved.isEmpty()
    }
}
