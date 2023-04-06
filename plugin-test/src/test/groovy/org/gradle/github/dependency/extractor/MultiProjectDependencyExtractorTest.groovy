package org.gradle.github.dependency.extractor


import org.gradle.test.fixtures.build.BuildTestFile
import org.gradle.test.fixtures.maven.MavenModule

class MultiProjectDependencyExtractorTest extends BaseExtractorTest {

    private MavenModule foo
    private String fooPurl
    private String fooGav
    private MavenModule bar

    def setup() {
        applyExtractorPlugin()
        establishEnvironmentVariables()

        foo = mavenRepo.module("org.test-published", "foo", "1.0").publish()
        fooPurl = purlFor(foo)
        fooGav = "org.test-published:foo:1.0"
        bar = mavenRepo.module("org.test-published", "bar", "2.0").publish()
    }

    void setupBuildFile(BuildTestFile buildTestFile) {
        buildTestFile.buildFile """
        repositories {
            maven { url "${mavenRepo.uri}" }
        }

        task validate {
            doLast {
                configurations.runtimeClasspath.resolvedConfiguration.files
            }
        }
        """
    }

    def "extracts dependencies from multiple unrelated projects"() {
        given:
        List<String> subprojects = ["a", "b"]
        multiProjectBuild("parent", subprojects) {
            List<BuildTestFile> projects = subprojects.collect { project(it) }.plus(it)
            projects.forEach {
                it.buildFile """
                apply plugin: 'java'
                dependencies {
                    implementation 'org.test-published:foo:1.0'
                }
                """
                setupBuildFile(it)
            }
        }

        when:
        succeeds("validate")

        then:
        manifestNames == ["project :", "project :a", "project :b"]

        def rootProjectManifest = gitHubManifest("project :")
        rootProjectManifest.sourceFile == "build.gradle"
        rootProjectManifest.resolved == ["org.test-published:foo:1.0"]
        rootProjectManifest.checkResolved("org.test-published:foo:1.0", fooPurl)

        subprojects.each { name ->
            def manifest = gitHubManifest("project :${name}")
            manifest.sourceFile == name + "/build.gradle"
            manifest.resolved == [fooGav]
            manifest.checkResolved(fooGav, fooPurl)
        }
    }

    def "extracts transitive project dependencies in multi-project build"() {
        given:
        multiProjectBuild("parent", ["a", "b", "c"]) {
            List<BuildTestFile> projects = []
            projects.add project("a").tap {
                buildFile """
                apply plugin: 'java-library'
                dependencies {
                    api 'org.test-published:foo:1.0'
                }
                """
            }
            projects.add project("b").tap {
                buildFile """
                apply plugin: 'java-library'
                dependencies {
                    api project(':a')
                }
                """
            }
            projects.add project("c").tap {
                buildFile """
                apply plugin: 'java'
                dependencies {
                    implementation project(':b')
                }
                """
            }
            projects.forEach {
                setupBuildFile(it)
            }
        }
        when:
        succeeds("classes")

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
        multiProjectBuild("parent", []) {
            project("buildSrc").tap {
                buildFile """
                apply plugin: 'java'
                group = 'org.test.buildSrc'
                version = '1.0'
                dependencies {
                    implementation 'org.test-published:foo:1.0'
                }
                """
                setupBuildFile(it)
            }

            buildFile """
            apply plugin: 'java'
            dependencies {
                implementation 'org.test-published:bar:2.0'
            }
            """
            setupBuildFile(it)
        }
        when:
        succeeds("validate")

        then:
        manifestNames == ["project :", "project :buildSrc"]

        def buildSrcManifest = gitHubManifest("project :buildSrc")
        buildSrcManifest.sourceFile == "buildSrc/build.gradle"
        buildSrcManifest.resolved == ["org.test-published:foo:1.0"]
        buildSrcManifest.checkResolved("org.test-published:foo:1.0", fooPurl)

        def projectManifest = gitHubManifest("project :")
        projectManifest.sourceFile == "build.gradle"
        projectManifest.resolved == ["org.test-published:bar:2.0"]
        projectManifest.checkResolved("org.test-published:bar:2.0", purlFor(bar))
    }

    def "project leveraging included builds"() {
        given:
        multiProjectBuild("parent", []) {
            includedBuild("included-child").tap {
                buildFile """
                apply plugin: 'java-library'

                group = 'org.test.included'
                version = '1.0'
                dependencies {
                    implementation 'org.test-published:foo:1.0'
                }
                """
                setupBuildFile(it)
            }

            buildFile """
            apply plugin: 'java'

            dependencies {
                implementation 'org.test.included:included-child'
            }
            """
            setupBuildFile(it)
            buildFile """
            tasks.validate {
                dependsOn gradle.includedBuild("included-child").task(":validate")
            }
            """
        }
        when:
        succeeds("validate")

        then:
        manifestNames == ["project :", "project :included-child"]

        def projectManifest = gitHubManifest("project :")
        projectManifest.sourceFile == "build.gradle"
        projectManifest.resolved == ["project :included-child", "org.test-published:foo:1.0"]
        projectManifest.checkResolved("project :included-child", "pkg:maven/org.test.included/included-child@1.0", [
                relationship: "direct",
                dependencies: ["org.test-published:foo:1.0"]
        ])
        projectManifest.checkResolved("org.test-published:foo:1.0", fooPurl, [
                relationship: "indirect",
                dependencies: []
        ])

        def includedManifest = gitHubManifest("project :included-child")
        includedManifest.sourceFile == "included-child/build.gradle"
        includedManifest.resolved == [fooGav]
        includedManifest.checkResolved(fooGav, fooPurl)
    }
}
