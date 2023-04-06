package org.gradle.github.dependency.extractor

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

    def "extracts implementation and test dependencies for a java project"() {
        given:
        def foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        def baz = mavenRepo.module("org.test", "baz", "1.0").publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
            implementation "org.test:bar:1.0"
            testImplementation "org.test:baz:1.0"
        }
        """
        when:
        succeeds("dependencies")

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.resolved == ["org.test:foo:1.0", "org.test:bar:1.0", "org.test:baz:1.0"]
        manifest.checkResolved("org.test:foo:1.0", purlFor(foo))
        manifest.checkResolved("org.test:bar:1.0", purlFor(bar))
        manifest.checkResolved("org.test:baz:1.0", purlFor(baz))
    }

    def "extracts only those dependencies resolved during project execution"() {
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
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.resolved == ["org.test:foo:1.0"] // "org.test:bar" is not in the runtime classpath, and so is not extracted
        manifest.checkResolved("org.test:foo:1.0", purlFor(foo))
    }

    def "extracts dependencies from custom configuration"() {
        given:
        def foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        singleProjectBuildWithDependencies """
        configurations {
            custom
        }
        dependencies {
            custom "org.test:foo:1.0"
            custom "org.test:bar:1.0"
        }
        """
        when:
        succeeds("dependencies")

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.resolved == ["org.test:foo:1.0", "org.test:bar:1.0"]
        manifest.checkResolved("org.test:foo:1.0", purlFor(foo))
        manifest.checkResolved("org.test:bar:1.0", purlFor(bar))
    }

    def "extracts transitive dependencies"() {
        given:
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(bar).publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
        }
        """
        when:
        succeeds("dependencies")

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.resolved == ["org.test:foo:1.0", "org.test:bar:1.0"]
        manifest.checkResolved("org.test:foo:1.0", purlFor(foo), [
                relationship: "direct",
                dependencies: ["org.test:bar:1.0"]
        ])
        manifest.checkResolved("org.test:bar:1.0", purlFor(bar), [
                relationship: "indirect",
                dependencies: []
        ])
    }

    def "extracts direct dependency for transitive dependency updated by constraint"() {
        given:
        def bar10 = mavenRepo.module("org.test", "bar", "1.0").publish()
        def bar11 = mavenRepo.module("org.test", "bar", "1.1").publish()
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(bar10).publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:foo:1.0"
            
            constraints {
                implementation "org.test:bar:1.1"
            }
        }
        """
        when:
        succeeds("dependencies")

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.resolved == ["org.test:foo:1.0", "org.test:bar:1.1"]
        manifest.checkResolved("org.test:foo:1.0", purlFor(foo), [
                relationship: "direct",
                dependencies: ["org.test:bar:1.1"]
        ])
        manifest.checkResolved("org.test:bar:1.1", purlFor(bar11), [
                relationship: "direct", // Constraint is a type of direct dependency
                dependencies: []
        ])
    }

    def "extracts both versions from build with two versions of the same dependency"() {
        given:
        def bar10 = mavenRepo.module("org.test", "bar", "1.0").publish()
        def bar11 = mavenRepo.module("org.test", "bar", "1.1").publish()
        singleProjectBuildWithDependencies """
        dependencies {
            implementation "org.test:bar:1.0"
            testImplementation "org.test:bar:1.1"
        }
        """
        when:
        succeeds("dependencies")

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.resolved == ["org.test:bar:1.0", "org.test:bar:1.1"]
        manifest.checkResolved("org.test:bar:1.0", purlFor(bar10))
        manifest.checkResolved("org.test:bar:1.1", purlFor(bar11))
    }

    def "extracts both versions from build with two versions of the same transitive dependency"() {
        given:
        def bar10 = mavenRepo.module("org.test", "bar", "1.0").publish()
        def bar11 = mavenRepo.module("org.test", "bar", "1.1").publish()
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(bar10).publish()
        singleProjectBuildWithDependencies """
        configurations {
            testCompileClasspath {
                resolutionStrategy.force("org.test:bar:1.1")
            }
        }
        dependencies {
            implementation "org.test:foo:1.0"
        }
        """
        when:
        succeeds("dependencies")

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.resolved == ["org.test:foo:1.0", "org.test:bar:1.0", "org.test:bar:1.1"]
        manifest.checkResolved("org.test:foo:1.0", purlFor(foo), [
                relationship: "direct",
                dependencies: ["org.test:bar:1.0", "org.test:bar:1.1"]
        ])
        manifest.checkResolved("org.test:bar:1.0", purlFor(bar10), [
                relationship: "indirect"
        ])
        manifest.checkResolved("org.test:bar:1.1", purlFor(bar11), [
                relationship: "indirect"
        ])
    }

    def "extracts direct and transitive dependencies from buildscript"() {
        given:
        def bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        def foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(bar).publish()
        singleProjectBuild("a") {
            buildFile """
            buildscript {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                
                dependencies {
                    classpath "org.test:foo:1.0"
                }        
            }
            apply plugin: 'java'
            """
        }
        when:
        succeeds("dependencies")

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.resolved == ["org.test:foo:1.0", "org.test:bar:1.0"]
        manifest.checkResolved("org.test:foo:1.0", purlFor(foo), [
                relationship: "direct",
                dependencies: ["org.test:bar:1.0"]
        ])
        manifest.checkResolved("org.test:bar:1.0", purlFor(bar), [
                relationship: "indirect",
                dependencies: []
        ])
    }
}
