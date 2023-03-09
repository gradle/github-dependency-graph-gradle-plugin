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
        def fooPurl = purlFor(foo)
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
        def testFoo = resolved[fooPurl]
        testFoo instanceof Map
        verifyAll(testFoo as Map) {
            purl == fooPurl
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with single dependency compiled & built"() {
        given:
        def foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        def fooPurl = purlFor(foo)
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
            def testFoo = resolved[fooPurl]
            testFoo instanceof Map
            verifyAll(testFoo as Map) {
                purl == fooPurl
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
        def fooPurl = purlFor(foo)
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        def barPurl = purlFor(bar)
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
        def testFoo = resolved[fooPurl] as Map
        verifyAll(testFoo) {
            purl == fooPurl
            relationship == "direct"
            dependencies == []
        }
        def testBar = resolved[barPurl] as Map
        verifyAll(testBar) {
            purl == barPurl
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with one dependency and one transitive"() {
        given:
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        def barPurl = purlFor(bar)
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(bar).publish()
        def fooPurl = purlFor(foo)
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
        def testFoo = resolved[fooPurl] as Map
        verifyAll(testFoo) {
            purl == fooPurl
            relationship == "direct"
            dependencies == [barPurl]
        }
        def testBar = resolved[barPurl] as Map
        verifyAll(testBar) {
            purl == barPurl
            relationship == "indirect"
            dependencies == []
        }
    }

    def "build with one dependency and one transitive when multiple configurations are resolved"() {
        given:
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        def barPurl = purlFor(bar)
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(bar).publish()
        def fooPurl = purlFor(foo)
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
            def testFoo = resolved[fooPurl] as Map
            verifyAll(testFoo) {
                purl == fooPurl
                relationship == "direct"
                dependencies == [barPurl]
            }
            def testBar = resolved[barPurl] as Map
            verifyAll(testBar) {
                purl == barPurl
                relationship == "indirect"
                dependencies == []
            }
        }
    }

    def "build with dependency updated transitively"() {
        given:
        mavenRepo.module("org.test", "bar", "1.0").publish()
        def barNewer = mavenRepo.module("org.test", "bar", "1.1").publish()
        def barNewerPurl = purlFor(barNewer)
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(barNewer).publish()
        def fooPurl = purlFor(foo)
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
        def testFoo = resolved[fooPurl] as Map
        verifyAll(testFoo) {
            purl == fooPurl
            relationship == "direct"
            dependencies == [barNewerPurl]
        }
        def testBar = resolved[barNewerPurl] as Map
        verifyAll(testBar) {
            purl == barNewerPurl
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with transitive dependency updated directly"() {
        given:
        def barOlder = mavenRepo.module("org.test", "bar", "1.0").publish()
        def bar = mavenRepo.module("org.test", "bar", "1.1").publish()
        def barPurl = purlFor(bar)
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(barOlder).publish()
        def fooPurl = purlFor(foo)
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
        def testFoo = resolved[fooPurl] as Map
        verifyAll(testFoo) {
            purl == fooPurl
            relationship == "direct"
            dependencies == [barPurl]
        }
        def testBarIndirect = resolved[barPurl] as Map
        verifyAll(testBarIndirect) {
            purl == barPurl
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with buildscript dependencies"() {
        given:
        def foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        def fooPurl = purlFor(foo)
        singleProjectBuildWithBuildscript """
        dependencies {
            classpath "org.test:foo:1.0"
        }
        """
        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def classpathManifest = jsonRepositorySnapshot(configuration: "classpath", buildscript: true)
        def buildScriptFile = classpathManifest.file as Map
        buildScriptFile.source_location == "build.gradle"
        def classpathResolved = classpathManifest.resolved as Map
        def testFoo = classpathResolved[fooPurl] as Map
        verifyAll(testFoo) {
            purl == fooPurl
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
