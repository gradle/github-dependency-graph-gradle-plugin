package org.gradle.github.dependencygraph


import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf

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
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)],
            "org.test:bar:1.0": [package_url: purlFor(bar)]
        ])
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
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)],
            "org.test:bar:1.0": [package_url: purlFor(bar)]
        ])

        where:
        task                                                     | description
        "ForceDependencyResolutionPlugin_resolveAllDependencies" | "All dependencies resolved"
        ":c:dependencies"                                        | "One project resolved"
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
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:bar:1.0": [package_url: purlFor(bar)],
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
                    resolutionStrategy.eachDependency { details ->
                        if (details.requested.group == 'org.test' && details.requested.name == 'bar') {
                            details.useVersion("1.1")
                        }
                    }
                }
            }
        """

        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
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
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)],
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
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:bar:1.0": [package_url: purlFor(bar)],
            "org.test:foo:1.0": [package_url: purlFor(foo)]
        ])
    }

    @IgnoreIf({
        // `includeBuild('.')` is not possible with Gradle < 6.1
        GradleVersion.version(testGradleVersion) < GradleVersion.version("6.1")
    })
    def "extracts dependencies from build that includes itself"() {
        given:
        settingsFile << """
            includeBuild('.')
"""
        buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation 'org.test:bar:1.0'
            }

"""

        when:
        run(":ForceDependencyResolutionPlugin_resolveAllDependencies")

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:bar:1.0": [package_url: purlFor(bar)]
        ])
    }
}
