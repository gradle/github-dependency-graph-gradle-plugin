package org.gradle.github.dependency.extractor


import org.gradle.test.fixtures.maven.MavenModule

class MultiProjectDependencyExtractorTest extends BaseExtractorTest {
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
        run()

        then:
        manifestNames == ["project :a", "project :b", "project :c"]

        def manifestA = gitHubManifest("project :a")
        manifestA.sourceFile == "a/build.gradle"
        manifestA.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)]
        ])

        def manifestB = gitHubManifest("project :b")
        manifestB.sourceFile == "b/build.gradle"
        manifestB.assertResolved([
            "project :a"      : [
                package_url : "pkg:maven/org.test/a@1.0",
                dependencies: ["org.test:foo:1.0"]
            ],
            "org.test:foo:1.0": [
                package_url : purlFor(foo),
                relationship: "indirect"
            ]
        ])

        def manifestC = gitHubManifest("project :c")
        manifestC.sourceFile == "c/build.gradle"
        manifestC.assertResolved([
            "project :b"      : [
                package_url : "pkg:maven/org.test/b@1.0",
                dependencies: ["project :a"]
            ],
            "project :a"      : [
                package_url : "pkg:maven/org.test/a@1.0",
                relationship: "indirect",
                dependencies: ["org.test:foo:1.0"]
            ],
            "org.test:foo:1.0": [
                package_url : purlFor(foo),
                relationship: "indirect"
            ]

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
            }
        """

        when:
        run()

        then:
        manifestNames == ["project :", "project :included-child"]

        def projectManifest = gitHubManifest("project :")
        projectManifest.sourceFile == "build.gradle"
        projectManifest.assertResolved([
            "project :included-child": [
                package_url : "pkg:maven/org.test.included/included-child@1.0",
                dependencies: ["org.test:foo:1.0"]
            ],
            "org.test:foo:1.0"       : [
                package_url : purlFor(foo),
                relationship: "indirect"
            ]
        ])

        def includedManifest = gitHubManifest("project :included-child")
        includedManifest.sourceFile == "included-child/build.gradle"
        includedManifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)]
        ])
    }
}
