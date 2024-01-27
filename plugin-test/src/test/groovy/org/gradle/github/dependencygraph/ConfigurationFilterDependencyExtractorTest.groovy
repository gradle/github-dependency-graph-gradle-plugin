package org.gradle.github.dependencygraph


import org.gradle.test.fixtures.maven.MavenModule

class ConfigurationFilterDependencyExtractorTest extends BaseExtractorTest {
    private MavenModule foo
    private MavenModule bar
    private MavenModule baz

    private File settingsFile
    private File buildFile

    def setup() {
        applyDependencyGraphPlugin()
        establishEnvironmentVariables()

        foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        baz = mavenRepo.module("org.test", "baz", "1.0").dependsOn(bar).publish()

        settingsFile = file("settings.gradle") << """
            rootProject.name = 'parent'    
        """

        buildFile = file("build.gradle") << """
            allprojects {
                group "org.test"
                version "1.0"

                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
        """
    }

    def "can filter projects to extract dependencies"() {
        given:
        settingsFile << "include 'a', 'b'"

        buildFile << """
            project(':a') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:foo:1.0'
                }
            }
            project(':b') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:bar:1.0'
                }
            }
        """

        when:
        executer.withArgument("-DDEPENDENCY_GRAPH_INCLUDE_PROJECTS=:b")
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:bar:1.0": [package_url: purlFor(bar)]
        ])
    }

    def "can filter configurations to extract dependencies"() {
        given:
        settingsFile << "include 'a', 'b'"

        buildFile << """
            project(':a') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:foo:1.0'
                    testImplementation 'org.test:baz:1.0'
                }
            }
            project(':b') {
                apply plugin: 'java-library'
                dependencies {
                    implementation 'org.test:bar:1.0'
                    testImplementation 'org.test:baz:1.0'
                }
            }
        """

        when:
        executer.withArgument("-DDEPENDENCY_GRAPH_INCLUDE_CONFIGURATIONS=compileClasspath")
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)],
            "org.test:bar:1.0": [package_url: purlFor(bar)]
        ])
    }

    def "can filter runtime projects to determine scope"() {
        given:
        settingsFile << "include 'a', 'b'"

        buildFile << """
            project(':a') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:foo:1.0'
                }
            }
            project(':b') {
                apply plugin: 'java-library'
                dependencies {
                    implementation 'org.test:bar:1.0'
                }
            }
        """

        when:
        executer.withArgument("-DDEPENDENCY_GRAPH_RUNTIME_PROJECTS=:a")
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo), scope: "runtime"],
            "org.test:bar:1.0": [package_url: purlFor(bar), scope: "development"],
        ])
    }

    def "can filter runtime configurations to determine scope"() {
        given:
        settingsFile << "include 'a', 'b'"

        buildFile << """
            project(':a') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:foo:1.0'
                    testImplementation 'org.test:baz:1.0'
                }
            }
            project(':b') {
                apply plugin: 'java-library'
                dependencies {
                    implementation 'org.test:bar:1.0'
                    testImplementation 'org.test:baz:1.0'
                }
            }
        """

        when:
        executer.withArgument("-DDEPENDENCY_GRAPH_RUNTIME_CONFIGURATIONS=compileClasspath")
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo), scope: "runtime"],
            "org.test:bar:1.0": [package_url: purlFor(bar), scope: "runtime"],
            "org.test:baz:1.0": [package_url: purlFor(baz), scope: "development", dependencies: ["org.test:bar:1.0"]]
        ])
    }

    def "can filter runtime configurations to determine scope"() {
        given:
        settingsFile << "include 'a', 'b'"

        buildFile << """
            project(':a') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:foo:1.0'
                    testImplementation 'org.test:baz:1.0'
                }
            }
            project(':b') {
                apply plugin: 'java-library'
                dependencies {
                    implementation 'org.test:bar:1.0'
                    testImplementation 'org.test:baz:1.0'
                }
            }
        """

        when:
        executer.withArgument("-DDEPENDENCY_GRAPH_RUNTIME_CONFIGURATIONS=compileClasspath")
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo), scope: "runtime"],
            "org.test:bar:1.0": [package_url: purlFor(bar), scope: "runtime"],
            "org.test:baz:1.0": [package_url: purlFor(baz), scope: "development", dependencies: ["org.test:bar:1.0"]]
        ])
    }

    def "can filter runtime projects and configurations to determine scope"() {
        given:
        settingsFile << "include 'a', 'b'"

        buildFile << """
            project(':a') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:foo:1.0'
                    testImplementation 'org.test:baz:1.0'
                }
            }
            project(':b') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:bar:1.0'
                    testImplementation 'org.test:baz:1.0'
                }
            }
        """

        when:
        executer
            .withArgument("-DDEPENDENCY_GRAPH_RUNTIME_CONFIGURATIONS=compileClasspath")
            .withArgument("-DDEPENDENCY_GRAPH_RUNTIME_PROJECTS=:a")
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo), scope: "runtime"],
            "org.test:bar:1.0": [package_url: purlFor(bar), scope: "development"],
            "org.test:baz:1.0": [package_url: purlFor(baz), scope: "development", dependencies: ["org.test:bar:1.0"]]
        ])
    }

}
