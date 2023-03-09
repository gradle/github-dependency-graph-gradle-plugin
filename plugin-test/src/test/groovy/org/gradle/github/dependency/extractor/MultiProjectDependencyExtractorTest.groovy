package org.gradle.github.dependency.extractor

import org.gradle.github.dependency.base.BaseExtractorTest
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.test.fixtures.maven.MavenModule

class MultiProjectDependencyExtractorTest extends BaseExtractorTest {

    private MavenModule foo
    private String fooPurl
    private MavenModule bar
    private String barPurl

    def setup() {
        applyExtractorPlugin()
        establishEnvironmentVariables()

        foo = mavenRepo.module("org.test-published", "foo", "1.0").publish()
        fooPurl = purlFor(foo)
        bar = mavenRepo.module("org.test-published", "bar", "2.0").publish()
        barPurl = purlFor(bar)
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

    def "multiple projects with single dependency each"() {
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
        def manifests = jsonManifests()
        manifests.size() == 3
        def parentRuntimeClasspath = jsonRepositorySnapshot(configuration: "runtimeClasspath")
        def parentRuntimeClasspathFile = parentRuntimeClasspath.file as Map
        parentRuntimeClasspathFile.source_location == "build.gradle"
        def parentClasspathResolved = parentRuntimeClasspath.resolved as Map
        def parentTestFoo = parentClasspathResolved[fooPurl] as Map
        verifyAll(parentTestFoo) {
            purl == this.fooPurl
            relationship == "direct"
            dependencies == []
        }
        subprojects.each { name ->
            def runtimeClasspath = jsonRepositorySnapshot(project: ':' + name, configuration: "runtimeClasspath")
            def runtimeClasspathFile = runtimeClasspath.file as Map
            runtimeClasspathFile.source_location == name + "/build.gradle"
            def classpathResolved = runtimeClasspath.resolved as Map
            def testFoo = classpathResolved[fooPurl] as Map
            verifyAll(testFoo) {
                purl == this.fooPurl
                relationship == "direct"
                dependencies == []
            }
        }
    }

    def "multi-project build where one project depends upon another"(String taskInvocation, boolean resolveProjectA) {
        given:
        multiProjectBuild("parent", ["a", "b"]) {
            List<BuildTestFile> projects = []
            projects.add project("a").tap {
                buildFile """
                apply plugin: 'java'
                dependencies {
                    implementation 'org.test-published:foo:1.0'
                }
                """
            }
            projects.add project("b").tap {
                buildFile """
                apply plugin: 'java'
                dependencies {
                    implementation project(':a')
                }
                """
            }
            projects.forEach {
                setupBuildFile(it)
            }
        }
        when:
        succeeds(taskInvocation)

        then:
        def manifests = jsonManifests()
        if (resolveProjectA) {
            manifests.size() == 2
            def aRuntimeClasspath = jsonRepositorySnapshot(project: ":a", configuration: "runtimeClasspath")
            def aRuntimeClasspathFile = aRuntimeClasspath.file as Map
            aRuntimeClasspathFile.source_location == "a/build.gradle"
            def aClasspathResolved = aRuntimeClasspath.resolved as Map
            def aTestFoo = aClasspathResolved[fooPurl] as Map
            verifyAll(aTestFoo) {
                purl == this.fooPurl
                relationship == "direct"
                dependencies == []
            }
        } else {
            manifests.size() == 1
        }
        def bRuntimeClasspath = jsonRepositorySnapshot(project: ":b", configuration: "runtimeClasspath")
        def bRuntimeClasspathFile = bRuntimeClasspath.file as Map
        bRuntimeClasspathFile.source_location == "b/build.gradle"
        def bClasspathResolved = bRuntimeClasspath.resolved as Map
        def bTestFoo = bClasspathResolved[fooPurl] as Map
        verifyAll(bTestFoo) {
            purl == this.fooPurl
            relationship == "indirect"
            dependencies == []
        }
        def aTestProjectPurl = "pkg:maven/org.test/a@1.0"
        def aTestProject = bClasspathResolved[aTestProjectPurl] as Map
        verifyAll(aTestProject) {
            purl == aTestProjectPurl
            relationship == "direct"
            dependencies == [this.fooPurl]
        }
        where:
        // Running just the 'validate' task on 'b' will not implicitly cause 'a' to be resolved.
        // This is because the configuration 'runtimeClasspath' on 'b' relies upon the 'runtimeElements' of 'a',
        // not the 'runtimeClasspath'. The 'runtimeElements' configuration is not itself resolvable.
        // https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph
        taskInvocation | resolveProjectA
        "validate"     | true
        ":b:validate"  | false
    }

    def "multi-project build with transitive project dependencies"(String taskInvocation) {
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
        succeeds(taskInvocation)

        then:
        def aTestProjectPurl = "pkg:maven/org.test/a@1.0"
        def bTestProjectPurl = "pkg:maven/org.test/b@1.0"

        def aCompileClasspath = jsonRepositorySnapshot(project: ":a", configuration: "compileClasspath")
        def aCompileClasspathFile = aCompileClasspath.file as Map
        aCompileClasspathFile.source_location == "a/build.gradle"
        def aClasspathResolved = aCompileClasspath.resolved as Map
        verifyAll(aClasspathResolved[fooPurl] as Map) {
            purl == this.fooPurl
            relationship == "direct"
            dependencies == []
        }

        def bCompileClasspath = jsonRepositorySnapshot(project: ":b", configuration: "compileClasspath")
        def bCompileClasspathFile = bCompileClasspath.file as Map
        bCompileClasspathFile.source_location == "b/build.gradle"
        def bClasspathResolved = bCompileClasspath.resolved as Map
        verifyAll(bClasspathResolved[fooPurl] as Map) {
            purl == this.fooPurl
            relationship == "indirect"
            dependencies == []
        }
        verifyAll(bClasspathResolved[aTestProjectPurl] as Map) {
            purl == aTestProjectPurl
            relationship == "direct"
            dependencies == [this.fooPurl]
        }

        def cCompileClasspath = jsonRepositorySnapshot(project: ":c", configuration: "compileClasspath")
        def cCompileClasspathFile = cCompileClasspath.file as Map
        cCompileClasspathFile.source_location == "c/build.gradle"
        def cClasspathResolved = cCompileClasspath.resolved as Map
        verifyAll(cClasspathResolved[fooPurl] as Map) {
            purl == this.fooPurl
            relationship == "indirect"
            dependencies == []
        }
        verifyAll(cClasspathResolved[aTestProjectPurl] as Map) {
            purl == aTestProjectPurl
            relationship == "indirect"
            dependencies == [this.fooPurl]
        }
        verifyAll(cClasspathResolved[bTestProjectPurl] as Map) {
            purl == bTestProjectPurl
            relationship == "direct"
            dependencies == [aTestProjectPurl]
        }
        where:
        taskInvocation | _
        "classes"      | _
        ":c:classes"   | _
    }

    def "project with buildSrc"() {
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
        Map manifests = jsonManifests()
        def buildSrcClasspath =
            manifests[manifestKey(build: ":buildSrc", configuration: "buildScriptClasspath")] // Gradle 8+
            ?: manifests[manifestKey(build: ":buildSrc", configuration: "runtimeClasspath")] // Gradle <=7

        def buildSrcRuntimeFile = buildSrcClasspath.file as Map
        buildSrcRuntimeFile.source_location == "buildSrc/build.gradle"
        def buildSrcClasspathResolved = buildSrcClasspath.resolved as Map
        def testFoo = buildSrcClasspathResolved[fooPurl] as Map
        verifyAll(testFoo) {
            purl == this.fooPurl
            relationship == "direct"
            dependencies == []
        }
        def runtimeClasspath = jsonRepositorySnapshot(configuration: "runtimeClasspath")
        def runtimeFile = runtimeClasspath.file as Map
        runtimeFile.source_location == "build.gradle"
        def runtimeClasspathResolved = runtimeClasspath.resolved as Map
        def testBar = runtimeClasspathResolved[barPurl] as Map
        verifyAll(testBar) {
            purl == this.barPurl
            relationship == "direct"
            dependencies == []
        }
    }

    def "project leveraging included builds"(boolean includedBuildTaskDependency, boolean resolveIncludedBuild) {
        given:
        multiProjectBuild("parent", []) {
            includedBuild("included-child").tap {
                buildFile """
                apply plugin: 'java'

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
            if (includedBuildTaskDependency) {
                buildFile """
                tasks.validate {
                    dependsOn gradle.includedBuild("included-child").task(":validate")
                }
                """
            }
        }
        when:
        succeeds("validate")

        then:
        def runtimeClasspath = jsonRepositorySnapshot(configuration: "runtimeClasspath")
        def runtimeFile = runtimeClasspath.file as Map
        verifyAll {
            runtimeFile.source_location == "build.gradle"
            def runtimeClasspathResolved = runtimeClasspath.resolved as Map
            def testFoo = runtimeClasspathResolved[fooPurl] as Map
            verifyAll(testFoo) {
                purl == this.fooPurl
                relationship == "indirect"
                dependencies == []
            }
            def includedChildPurl = "pkg:maven/org.test.included/included-child@1.0"
            def testIncludedChild = runtimeClasspathResolved[includedChildPurl] as Map
            verifyAll(testIncludedChild) {
                purl == includedChildPurl
                relationship == "direct"
                dependencies == [this.fooPurl]
            }
        }
        if (resolveIncludedBuild) {
            def includedChildRuntimeClasspath = jsonRepositorySnapshot(build: ":included-child", configuration: "runtimeClasspath")
            def includedChildRuntimeFile = includedChildRuntimeClasspath.file as Map
            verifyAll {
                includedChildRuntimeFile.source_location == "included-child/build.gradle"
                def includedChildRuntimeClasspathResolved = includedChildRuntimeClasspath.resolved as Map

                def testFoo = includedChildRuntimeClasspathResolved[fooPurl] as Map
                verifyAll(testFoo) {
                    purl == this.fooPurl
                    relationship == "direct"
                    dependencies == []
                }
            }
        }

        where:
        includedBuildTaskDependency | resolveIncludedBuild
        false                       | false
        true                        | true
    }
}
