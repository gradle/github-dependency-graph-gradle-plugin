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
        settingsFile << "include 'a', 'b', 'c'"

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
            project(':c') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:baz:1.0'
                }
            }
        """

        when:
        executer.withArgument("-DDEPENDENCY_GRAPH_INCLUDE_PROJECTS=:b")
        run()

        then:
        gitHubManifest().assertResolved(["org.test:bar:1.0"])

        when:
        resetArguments()
        executer.withArgument("-DDEPENDENCY_GRAPH_INCLUDE_PROJECTS=:[ab]")
        run()

        then:
        gitHubManifest().assertResolved(["org.test:foo:1.0", "org.test:bar:1.0"])

        when:
        resetArguments()
        executer.withArgument("-DDEPENDENCY_GRAPH_EXCLUDE_PROJECTS=:[bc]")
        run()

        then:
        gitHubManifest().assertResolved(["org.test:foo:1.0"])

        when:
        resetArguments()
        executer.withArgument("-DDEPENDENCY_GRAPH_INCLUDE_PROJECTS=:[ab]")
        executer.withArgument("-DDEPENDENCY_GRAPH_EXCLUDE_PROJECTS=:b")
        run()

        then:
        gitHubManifest().assertResolved(["org.test:foo:1.0"])
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
        gitHubManifest().assertResolved(["org.test:foo:1.0", "org.test:bar:1.0"])

        when:
        resetArguments()
        executer.withArgument("-DDEPENDENCY_GRAPH_EXCLUDE_CONFIGURATIONS=test(Compile|Runtime)Classpath")
        run()

        then:
        gitHubManifest().assertResolved(["org.test:foo:1.0", "org.test:bar:1.0"])
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
        executer.withArgument("-DDEPENDENCY_GRAPH_RUNTIME_INCLUDE_PROJECTS=:a")
        run()

        then:
        gitHubManifest().assertResolved([
            "org.test:foo:1.0": [scope: "runtime"],
            "org.test:bar:1.0": [scope: "development"]
        ])

        when:
        resetArguments()
        executer.withArgument("-DDEPENDENCY_GRAPH_RUNTIME_EXCLUDE_PROJECTS=:b")
        run()

        then:
        gitHubManifest().assertResolved([
            "org.test:foo:1.0": [scope: "runtime"],
            "org.test:bar:1.0": [scope: "development"]
        ])

        when:
        resetArguments()
        executer.withArgument("-DDEPENDENCY_GRAPH_RUNTIME_INCLUDE_PROJECTS=:[ab]")
        executer.withArgument("-DDEPENDENCY_GRAPH_RUNTIME_EXCLUDE_PROJECTS=:b")
        run()

        then:
        gitHubManifest().assertResolved([
            "org.test:foo:1.0": [scope: "runtime"],
            "org.test:bar:1.0": [scope: "development"]
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
        executer.withArgument("-DDEPENDENCY_GRAPH_RUNTIME_INCLUDE_CONFIGURATIONS=compileClasspath")
        run()

        then:
        gitHubManifest().assertResolved([
            "org.test:foo:1.0": [scope: "runtime"],
            "org.test:bar:1.0": [scope: "runtime"],
            "org.test:baz:1.0": [scope: "development", dependencies: ["org.test:bar:1.0"]]
        ])

        when:
        resetArguments()
        executer.withArgument("-DDEPENDENCY_GRAPH_RUNTIME_INCLUDE_CONFIGURATIONS=.*Classpath")
        run()

        then:
        gitHubManifest().assertResolved([
            "org.test:foo:1.0": [scope: "runtime"],
            "org.test:bar:1.0": [scope: "runtime"],
            "org.test:baz:1.0": [scope: "runtime", dependencies: ["org.test:bar:1.0"]]
        ])

        when:
        resetArguments()
        executer.withArgument("-DDEPENDENCY_GRAPH_RUNTIME_INCLUDE_CONFIGURATIONS=.*Classpath")
        executer.withArgument("-DDEPENDENCY_GRAPH_RUNTIME_EXCLUDE_CONFIGURATIONS=test(Compile|Runtime)Classpath")
        run()

        then:
        gitHubManifest().assertResolved([
            "org.test:foo:1.0": [scope: "runtime"],
            "org.test:bar:1.0": [scope: "runtime"],
            "org.test:baz:1.0": [scope: "development", dependencies: ["org.test:bar:1.0"]]
        ])
    }

    def "can filter runtime projects and configurations to determine scope"() {
        given:
        settingsFile << "include 'a', 'b', 'c'"

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
            project(':b') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:baz:1.0'
                }
            }
        """

        when:
        executer
            .withArgument("-DDEPENDENCY_GRAPH_RUNTIME_INCLUDE_PROJECTS=:[ab]")
            .withArgument("-DDEPENDENCY_GRAPH_RUNTIME_INCLUDE_CONFIGURATIONS=.*Classpath")
            .withArgument("-DDEPENDENCY_GRAPH_RUNTIME_EXCLUDE_PROJECTS=:b")
            .withArgument("-DDEPENDENCY_GRAPH_RUNTIME_EXCLUDE_CONFIGURATIONS=test(Compile|Runtime)Classpath")
        run()

        then:
        gitHubManifest().assertResolved([
            "org.test:foo:1.0": [scope: "runtime"],
            "org.test:bar:1.0": [scope: "development"],
            "org.test:baz:1.0": [scope: "development", dependencies: ["org.test:bar:1.0"]]
        ])
    }

    def "does not attempt to resolve excluded configurations"() {
        given:
        settingsFile << "include 'a', 'b'"

        buildFile << """
            project(':a') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:foo:1.0'
                }
                configurations.all {
                    incoming.beforeResolve {
                        throw new RuntimeException("Should not resolve project :a")
                    }
                }
            }
            project(':b') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:bar:1.0'
                    testImplementation 'org.test:baz:1.0'
                }
                configurations.testCompileClasspath {
                    incoming.beforeResolve {
                        throw new RuntimeException("Should not resolve configuration 'testCompileClasspath'")
                    }
                }
            }
        """

        when:
        executer.withArgument("-DDEPENDENCY_GRAPH_EXCLUDE_PROJECTS=:a")
        executer.withArgument("-DDEPENDENCY_GRAPH_EXCLUDE_CONFIGURATIONS=test(Compile|Runtime)Classpath")
        run()

        then:
        gitHubManifest().assertResolved(["org.test:bar:1.0"])
    }

}
