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

    def "build with implementation and test dependency"() {
        given:
        def foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
            testImplementation "org.test:bar:1.0"
        }
        """
        when:
        succeeds("dependencies")

        then:
        def manifest = jsonManifest("project :")
        (manifest.file as Map).source_location == "build.gradle"
        def resolved = manifest.resolved as Map
        resolved.keySet() == ["org.test:foo:1.0", "org.test:bar:1.0"] as Set
        verifyAll(resolved["org.test:foo:1.0"] as Map) {
            package_url == purlFor(foo)
            relationship == "direct"
            dependencies == []
        }
        verifyAll(resolved["org.test:bar:1.0"] as Map) {
            package_url == purlFor(bar)
            relationship == "direct"
            dependencies == []
        }
    }

    def "build with dependency compiled & built"() {
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
        def manifest = jsonManifest("project :")
        verifyAll {
            (manifest.file as Map).source_location == "build.gradle"
            def resolved = manifest.resolved as Map
            resolved.keySet() == ["org.test:foo:1.0"] as Set
            verifyAll(resolved["org.test:foo:1.0"] as Map) {
                package_url == purlFor(foo)
                relationship == "direct"
                dependencies == []
            }
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
        def manifest = jsonManifest("project :")
        (manifest.file as Map).source_location == "build.gradle"
        def resolved = manifest.resolved as Map
        resolved.keySet() == ["org.test:foo:1.0", "org.test:bar:1.0"] as Set
        verifyAll(resolved["org.test:foo:1.0"] as Map) {
            package_url == purlFor(foo)
            relationship == "direct"
            dependencies == []
        }
        verifyAll(resolved["org.test:bar:1.0"] as Map) {
            package_url == purlFor(bar)
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
        def manifest = jsonManifest("project :")
        (manifest.file as Map).source_location == "build.gradle"
        def resolved = manifest.resolved as Map
        resolved.keySet() == ["org.test:foo:1.0", "org.test:bar:1.0"] as Set
        verifyAll(resolved["org.test:foo:1.0"] as Map) {
            package_url == purlFor(foo)
            relationship == "direct"
            dependencies == ["org.test:bar:1.0"]
        }
        verifyAll(resolved["org.test:bar:1.0"] as Map) {
            package_url == purlFor(bar)
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
        def manifest = jsonManifest("project :")
        (manifest.file as Map).source_location == "build.gradle"
        def resolved = manifest.resolved as Map
        resolved.keySet() == ["org.test:foo:1.0", "org.test:bar:1.0"] as Set
        verifyAll(resolved["org.test:foo:1.0"] as Map) {
            package_url == purlFor(foo)
            relationship == "direct"
            dependencies == ["org.test:bar:1.0"]
        }
        verifyAll(resolved["org.test:bar:1.0"] as Map) {
            package_url == purlFor(bar)
            relationship == "indirect"
            dependencies == []
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
        def manifest = jsonManifest("project :")
        (manifest.file as Map).source_location == "build.gradle"
        def resolved = manifest.resolved as Map
        resolved.keySet() == ["org.test:foo:1.0", "org.test:bar:1.1"] as Set
        def testFoo = resolved["org.test:foo:1.0"] as Map
        verifyAll(testFoo) {
            package_url == purlFor(foo)
            relationship == "direct"
            dependencies == ["org.test:bar:1.1"]
        }
        def testBar = resolved["org.test:bar:1.1"] as Map
        verifyAll(testBar) {
            package_url == purlFor(barNewer)
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
        def manifest = jsonManifest("project :")
        (manifest.file as Map).source_location == "build.gradle"
        def resolved = manifest.resolved as Map
        resolved.keySet() == ["org.test:foo:1.0", "org.test:bar:1.1"] as Set
        verifyAll(resolved["org.test:foo:1.0"] as Map) {
            package_url == purlFor(foo)
            relationship == "direct"
            dependencies == ["org.test:bar:1.1"]
        }
        verifyAll(resolved["org.test:bar:1.1"] as Map) {
            package_url == purlFor(bar)
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
        def manifest = jsonManifest("project :")
        (manifest.file as Map).source_location == "build.gradle"
        def resolved = manifest.resolved as Map
        resolved.keySet() == ["org.test:foo:1.0"] as Set
        verifyAll(resolved["org.test:foo:1.0"] as Map) {
            package_url == purlFor(foo)
            relationship == "direct"
            dependencies == []
        }
    }
}
