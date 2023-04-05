package org.gradle.github.dependency.extractor

import org.gradle.test.fixtures.maven.MavenModule

class SingleProjectDependencyExtractorTest extends BaseExtractorTest {
    private MavenModule foo
    private MavenModule bar
    private MavenModule baz
    private File settingsFile
    private File buildFile

    def setup() {
        applyExtractorPlugin()
        establishEnvironmentVariables()

        foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        baz = mavenRepo.module("org.test", "baz", "1.0").dependsOn(bar).publish()

        settingsFile = file("settings.gradle") << """
            rootProject.name = 'a'    
        """

        buildFile = file("build.gradle") << """
            apply plugin: 'java'

            repositories {
                maven { url "${mavenRepo.uri}" }
            }            
        """
    }

    def "extracts implementation and test dependencies for a java project"() {
        given:
        buildFile << """
        dependencies {
            implementation "org.test:foo:1.0"
            implementation "org.test:bar:1.0"
            testImplementation "org.test:baz:1.0"
        }
        """

        when:
        run()

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.assertResolved([
            "org.test:foo:1.0": [
                package_url: purlFor(foo)
            ],
            "org.test:bar:1.0": [
                package_url: purlFor(bar)
            ],
            "org.test:baz:1.0": [
                package_url : purlFor(baz),
                dependencies: ["org.test:bar:1.0"]
            ]
        ])
    }

    def "extracts only those dependencies resolved during project execution"() {
        given:
        buildFile << """
        dependencies {
            implementation "org.test:foo:1.0"
            testImplementation "org.test:bar:1.0"
        }
        """

        when:
        executer.withTasks("dependencies", "--configuration", "runtimeClasspath")
        run()

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)]
        ])
    }

    def "extracts dependencies from custom configuration"() {
        given:
        buildFile << """
        configurations {
            custom
        }
        dependencies {
            custom "org.test:foo:1.0"
            custom "org.test:bar:1.0"
        }
        """
        when:
        run()

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.assertResolved([
            "org.test:foo:1.0": [
                package_url: purlFor(foo)
            ],
            "org.test:bar:1.0": [
                package_url: purlFor(bar)
            ]
        ])
    }

    def "extracts transitive dependencies"() {
        given:
        def foo2 = mavenRepo.module("org.test", "foo", "2.0").dependsOn(bar).publish()
        buildFile << """
        dependencies {
            implementation "org.test:foo:2.0"
        }
        """
        when:
        run()

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.assertResolved([
            "org.test:foo:2.0": [
                package_url : purlFor(foo2),
                dependencies: ["org.test:bar:1.0"]
            ],
            "org.test:bar:1.0": [
                package_url : purlFor(bar),
                relationship: "indirect"
            ]
        ])
    }

    def "extracts direct dependency for transitive dependency updated by constraint"() {
        given:
        def bar11 = mavenRepo.module("org.test", "bar", "1.1").publish()
        buildFile << """
        dependencies {
            implementation "org.test:baz:1.0"
            
            constraints {
                implementation "org.test:bar:1.1"
            }
        }
        """
        when:
        run()

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"
        manifest.assertResolved([
            "org.test:baz:1.0": [
                package_url : purlFor(baz),
                dependencies: ["org.test:bar:1.1"]
            ],
            "org.test:bar:1.1": [
                package_url : purlFor(bar11),
                relationship: "direct" // Constraint creates a direct dependency relationship
            ]
        ])
    }

    def "extracts both versions from build with two versions of the same dependency"() {
        given:
        def bar11 = mavenRepo.module("org.test", "bar", "1.1").publish()
        buildFile << """
        dependencies {
            implementation "org.test:bar:1.0"
            testImplementation "org.test:bar:1.1"
        }
        """
        when:
        run()

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.assertResolved([
            "org.test:bar:1.0": [
                package_url: purlFor(bar)
            ],
            "org.test:bar:1.1": [
                package_url: purlFor(bar11)
            ]
        ])
    }

    def "extracts both versions from build with two versions of the same transitive dependency"() {
        given:
        def bar11 = mavenRepo.module("org.test", "bar", "1.1").publish()
        buildFile << """
        configurations {
            testCompileClasspath {
                resolutionStrategy.force("org.test:bar:1.1")
            }
        }
        dependencies {
            implementation "org.test:baz:1.0"
        }
        """
        when:
        run()

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.assertResolved([
            "org.test:baz:1.0": [
                package_url : purlFor(baz),
                dependencies: ["org.test:bar:1.0", "org.test:bar:1.1"]
            ],
            "org.test:bar:1.0": [
                package_url : purlFor(bar),
                relationship: "indirect"
            ],
            "org.test:bar:1.1": [
                package_url : purlFor(bar11),
                relationship: "indirect"
            ]
        ])
    }

    def "extracts direct and transitive dependencies from buildscript"() {
        given:
        buildFile << """
            buildscript {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                
                dependencies {
                    classpath "org.test:baz:1.0"
                }        
            }
        """

        when:
        run()

        then:
        def manifest = gitHubManifest("project :")
        manifest.sourceFile == "build.gradle"

        manifest.assertResolved([
            "org.test:baz:1.0": [
                package_url : purlFor(baz),
                dependencies: ["org.test:bar:1.0"]
            ],
            "org.test:bar:1.0": [
                package_url : purlFor(bar),
                relationship: "indirect"
            ]
        ])
    }
}
