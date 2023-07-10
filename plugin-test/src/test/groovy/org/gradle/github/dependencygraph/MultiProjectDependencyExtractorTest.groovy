package org.gradle.github.dependencygraph


import org.gradle.test.fixtures.maven.MavenModule

class MultiProjectDependencyExtractorTest extends BaseExtractorTest {
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

    def "extracts dependencies from multiple unrelated projects"() {
        given:
        settingsFile << "include 'a', 'b'"

        buildFile << """
            allprojects {
                apply plugin: 'java'
                dependencies {
                    implementation 'org.test:foo:1.0'
                }
            }
        """

        when:
        run()

        then:
        manifestNames == ["project :", "project :a", "project :b"]

        def rootProjectManifest = gitHubManifest("project :")
        rootProjectManifest.sourceFile == "build.gradle"
        rootProjectManifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)]
        ])

        ["a", "b"].each { name ->
            def manifest = gitHubManifest("project :${name}")
            manifest.sourceFile == name + "/build.gradle"
            manifest.assertResolved([
                "org.test:foo:1.0": [package_url: purlFor(foo)]
            ])
        }
    }

    def "extracts transitive project dependencies in multi-project build with #description"() {
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
                    api project(':a')
                }
            }
            project(':c') {
                apply plugin: 'java'
                dependencies {
                    implementation project(':b')
                    implementation 'org.test:bar:1.0'
                }
            }
        """

        when:
        run(task)

        then:
        manifestNames == ["project :a", "project :c"]

        def manifestA = gitHubManifest("project :a")
        manifestA.sourceFile == "a/build.gradle"
        manifestA.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)]
        ])

        def manifestC = gitHubManifest("project :c")
        manifestC.sourceFile == "c/build.gradle"
        manifestC.assertResolved([
            "org.test:bar:1.0": [package_url: purlFor(bar)]
        ])

        where:
        task                                                  | description
        "GitHubDependencyGraphPlugin_generateDependencyGraph" | "All dependencies resolved"
        ":c:dependencies"                                     | "One project resolved"
    }

    def "extracts direct dependency for transitive dependency updated by constraint"() {
        given:
        def bar11 = mavenRepo.module("org.test", "bar", "1.1").publish()
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:bar:1.0'
                }
            }
            project(':b') {
                apply plugin: 'java-library'
                dependencies {
                    api project(':a')
                    constraints {
                        api "org.test:bar:1.1"
                    }
                }
            }
        """

        when:
        run()

        then:
        manifestNames == ["project :a", "project :b"]

        def manifestA = gitHubManifest("project :a")
        manifestA.sourceFile == "a/build.gradle"
        manifestA.assertResolved([
            "org.test:bar:1.0": [package_url: purlFor(bar)],
            "org.test:bar:1.1": [package_url: purlFor(bar11)]
        ])

        def manifestB = gitHubManifest("project :b")
        manifestB.sourceFile == "b/build.gradle"
        manifestB.assertResolved([
            "org.test:bar:1.1": [package_url: purlFor(bar11)]
        ])
    }

    def "extracts all versions for transitive dependency updated by rule"() {
        given:
        def bar11 = mavenRepo.module("org.test", "bar", "1.1").publish()
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test:bar:1.0'
                }
            }
            project(':b') {
                apply plugin: 'java-library'
                dependencies {
                    api project(':a')
                }
                configurations.all {
                    resolutionStrategy.dependencySubstitution {
                        substitute module('org.test:bar:1.0') using module('org.test:bar:1.1')
                    }
                }
            }
        """

        when:
        run()

        then:
        manifestNames == ["project :a"]

        def manifestA = gitHubManifest("project :a")
        manifestA.sourceFile == "a/build.gradle"
        manifestA.assertResolved([
            "org.test:bar:1.0": [package_url: purlFor(bar)],
            "org.test:bar:1.1": [package_url: purlFor(bar11)]
        ])
    }


    def "extracts dependencies from buildSrc project"() {
        given:
        file("buildSrc/settings.gradle") << "rootProject.name = 'buildSrc'"
        file("buildSrc/build.gradle") << """
            apply plugin: 'java'
            group = 'org.test.buildSrc'
            version = '1.0'
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                implementation 'org.test:foo:1.0'
            }
        """

        buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation 'org.test:bar:1.0'
            }
        """

        when:
        run()

        then:
        manifestNames == ["project :", "project :buildSrc"]

        def buildSrcManifest = gitHubManifest("project :buildSrc")
        buildSrcManifest.sourceFile == "buildSrc/build.gradle"
        buildSrcManifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)]
        ])

        def projectManifest = gitHubManifest("project :")
        projectManifest.sourceFile == "build.gradle"
        projectManifest.assertResolved([
            "org.test:bar:1.0": [package_url: purlFor(bar)]
        ])
    }

    def "extracts dependencies from included build"() {
        given:
        file("included-child/settings.gradle") << "rootProject.name = 'included-child'"
        file("included-child/build.gradle") << """
            apply plugin: 'java-library'
            group = 'org.test.included'
            version = '1.0'

            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                implementation 'org.test:foo:1.0'
            }
        """

        settingsFile << "includeBuild 'included-child'"
        buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation 'org.test.included:included-child'
                implementation 'org.test:bar:1.0'
            }
        """

        when:
        run()

        then:
        manifestNames == ["project :", "project :included-child"]

        def projectManifest = gitHubManifest("project :")
        projectManifest.sourceFile == "build.gradle"
        projectManifest.assertResolved([
            "org.test:bar:1.0": [package_url: purlFor(bar)]
        ])

        def includedManifest = gitHubManifest("project :included-child")
        includedManifest.sourceFile == "included-child/build.gradle"
        includedManifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)]
        ])
    }
}
