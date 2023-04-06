package org.gradle.github.dependency.extractor


import org.gradle.test.fixtures.maven.MavenModule

class MultiProjectDependencyExtractorTest extends BaseExtractorTest {
    private MavenModule foo
    private MavenModule bar
    private MavenModule baz

    private String fooPurl
    private String fooGav

    private File settingsFile
    private File buildFile

    def setup() {
        applyExtractorPlugin()
        establishEnvironmentVariables()

        foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        baz = mavenRepo.module("org.test", "baz", "1.0").dependsOn(bar).publish()

        fooPurl = purlFor(foo)
        fooGav = "org.test:foo:1.0"


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
        
                task validate {
                    dependsOn "dependencies"
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
        succeeds("validate")

        then:
        manifestNames == ["project :", "project :a", "project :b"]

        def rootProjectManifest = gitHubManifest("project :")
        rootProjectManifest.sourceFile == "build.gradle"
        rootProjectManifest.resolved == ["org.test:foo:1.0"]
        rootProjectManifest.checkResolved("org.test:foo:1.0", fooPurl)

        ["a", "b"].each { name ->
            def manifest = gitHubManifest("project :${name}")
            manifest.sourceFile == name + "/build.gradle"
            manifest.resolved == [fooGav]
            manifest.checkResolved(fooGav, fooPurl)
        }
    }

    def "extracts transitive project dependencies in multi-project build"() {
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
                }
            }
        """

        when:
        succeeds("validate")

        then:
        manifestNames == ["project :a", "project :b", "project :c"]

        def manifestA = gitHubManifest("project :a")
        manifestA.sourceFile == "a/build.gradle"
        manifestA.resolved == [fooGav]
        manifestA.checkResolved(fooGav, fooPurl)

        def manifestB = gitHubManifest("project :b")
        manifestB.sourceFile == "b/build.gradle"
        manifestB.resolved == ["project :a", fooGav]
        manifestB.checkResolved("project :a", "pkg:maven/org.test/a@1.0", [
                relationship: "direct",
                dependencies: [fooGav]
        ])
        manifestB.checkResolved(fooGav, fooPurl, [
                relationship: "indirect",
                dependencies: []
        ])

        def manifestC = gitHubManifest("project :c")
        manifestC.sourceFile == "c/build.gradle"
        manifestC.resolved == ["project :b", "project :a", fooGav]
        manifestC.checkResolved("project :b", "pkg:maven/org.test/b@1.0", [
                relationship: "direct",
                dependencies: ["project :a"]
        ])
        manifestC.checkResolved("project :a", "pkg:maven/org.test/a@1.0", [
                relationship: "indirect",
                dependencies: [fooGav]
        ])
        manifestC.checkResolved(fooGav, fooPurl, [
                relationship: "indirect",
                dependencies: []
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
        succeeds("validate")

        then:
        manifestNames == ["project :", "project :buildSrc"]

        def buildSrcManifest = gitHubManifest("project :buildSrc")
        buildSrcManifest.sourceFile == "buildSrc/build.gradle"
        buildSrcManifest.resolved == ["org.test:foo:1.0"]
        buildSrcManifest.checkResolved("org.test:foo:1.0", fooPurl)

        def projectManifest = gitHubManifest("project :")
        projectManifest.sourceFile == "build.gradle"
        projectManifest.resolved == ["org.test:bar:1.0"]
        projectManifest.checkResolved("org.test:bar:1.0", purlFor(bar))
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
            }
            tasks.validate {
                dependsOn gradle.includedBuild("included-child").task(":dependencies")
            }
        """

        when:
        succeeds("validate")

        then:
        manifestNames == ["project :", "project :included-child"]

        def projectManifest = gitHubManifest("project :")
        projectManifest.sourceFile == "build.gradle"
        projectManifest.resolved == ["project :included-child", "org.test:foo:1.0"]
        projectManifest.checkResolved("project :included-child", "pkg:maven/org.test.included/included-child@1.0", [
                relationship: "direct",
                dependencies: ["org.test:foo:1.0"]
        ])
        projectManifest.checkResolved("org.test:foo:1.0", fooPurl, [
                relationship: "indirect",
                dependencies: []
        ])

        def includedManifest = gitHubManifest("project :included-child")
        includedManifest.sourceFile == "included-child/build.gradle"
        includedManifest.resolved == [fooGav]
        includedManifest.checkResolved(fooGav, fooPurl)
    }
}
