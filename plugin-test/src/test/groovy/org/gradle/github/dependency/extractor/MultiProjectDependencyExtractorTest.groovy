package org.gradle.github.dependency.extractor

import org.gradle.github.dependency.base.BaseExtractorTest
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
        fooGav = gavFor(foo)
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
        def parentRuntimeClasspath = jsonManifest("project :")
        def parentRuntimeClasspathFile = parentRuntimeClasspath.file as Map
        parentRuntimeClasspathFile.source_location == "build.gradle"
        def parentClasspathResolved = parentRuntimeClasspath.resolved as Map
        def parentTestFoo = parentClasspathResolved[gavFor(foo)] as Map
        verifyAll(parentTestFoo) {
            package_url == this.fooPurl
            relationship == "direct"
            dependencies == []
        }
        subprojects.each { name ->
            def runtimeClasspath = jsonManifest("project :${name}")
            def runtimeClasspathFile = runtimeClasspath.file as Map
            runtimeClasspathFile.source_location == name + "/build.gradle"
            def classpathResolved = runtimeClasspath.resolved as Map
            def testFoo = classpathResolved[gavFor(foo)] as Map
            verifyAll(testFoo) {
                package_url == this.fooPurl
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
            def aRuntimeClasspath = jsonManifest("project :a")
            def aRuntimeClasspathFile = aRuntimeClasspath.file as Map
            aRuntimeClasspathFile.source_location == "a/build.gradle"
            def aClasspathResolved = aRuntimeClasspath.resolved as Map
            def aTestFoo = aClasspathResolved[fooGav] as Map
            verifyAll(aTestFoo) {
                package_url == this.fooPurl
                relationship == "direct"
                dependencies == []
            }
        } else {
            manifests.size() == 1
        }
        def bRuntimeClasspath = jsonManifest("project :b")
        def bRuntimeClasspathFile = bRuntimeClasspath.file as Map
        bRuntimeClasspathFile.source_location == "b/build.gradle"
        def bClasspathResolved = bRuntimeClasspath.resolved as Map
        def bTestFoo = bClasspathResolved[fooGav] as Map
        verifyAll(bTestFoo) {
            package_url == this.fooPurl
            relationship == "indirect"
            dependencies == []
        }
        def aTestProjectPurl = "pkg:maven/org.test/a@1.0"
        def aTestProject = bClasspathResolved["project :a"] as Map
        verifyAll(aTestProject) {
            package_url == aTestProjectPurl
            relationship == "direct"
            dependencies == [this.fooGav]
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

        def aCompileClasspath = jsonManifest("project :a")
        def aCompileClasspathFile = aCompileClasspath.file as Map
        aCompileClasspathFile.source_location == "a/build.gradle"
        def aClasspathResolved = aCompileClasspath.resolved as Map
        verifyAll(aClasspathResolved[fooGav] as Map) {
            package_url == this.fooPurl
            relationship == "direct"
            dependencies == []
        }

        def bCompileClasspath = jsonManifest("project :b")
        def bCompileClasspathFile = bCompileClasspath.file as Map
        bCompileClasspathFile.source_location == "b/build.gradle"
        def bClasspathResolved = bCompileClasspath.resolved as Map
        verifyAll(bClasspathResolved[fooGav] as Map) {
            package_url == this.fooPurl
            relationship == "indirect"
            dependencies == []
        }
        verifyAll(bClasspathResolved["project :a"] as Map) {
            package_url == aTestProjectPurl
            relationship == "direct"
            dependencies == [this.fooGav]
        }

        def cCompileClasspath = jsonManifest("project :c")
        def cCompileClasspathFile = cCompileClasspath.file as Map
        cCompileClasspathFile.source_location == "c/build.gradle"
        def cClasspathResolved = cCompileClasspath.resolved as Map
        verifyAll(cClasspathResolved[fooGav] as Map) {
            package_url == this.fooPurl
            relationship == "indirect"
            dependencies == []
        }
        verifyAll(cClasspathResolved["project :a"] as Map) {
            package_url == aTestProjectPurl
            relationship == "indirect"
            dependencies == [this.fooGav]
        }
        verifyAll(cClasspathResolved["project :b"] as Map) {
            package_url == bTestProjectPurl
            relationship == "direct"
            dependencies == ["project :a"]
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
        def buildSrcClasspath = manifests["project :buildSrc"]

        def buildSrcRuntimeFile = buildSrcClasspath.file as Map
        buildSrcRuntimeFile.source_location == "buildSrc/build.gradle"
        def buildSrcClasspathResolved = buildSrcClasspath.resolved as Map
        def testFoo = buildSrcClasspathResolved[fooGav] as Map
        verifyAll(testFoo) {
            package_url == this.fooPurl
            relationship == "direct"
            dependencies == []
        }
        def runtimeClasspath = jsonManifest("project :")
        def runtimeFile = runtimeClasspath.file as Map
        runtimeFile.source_location == "build.gradle"
        def runtimeClasspathResolved = runtimeClasspath.resolved as Map
        def testBar = runtimeClasspathResolved[gavFor(bar)] as Map
        verifyAll(testBar) {
            package_url == purlFor(this.bar)
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
        def runtimeClasspath = jsonManifest("project :")
        def runtimeFile = runtimeClasspath.file as Map
        verifyAll {
            runtimeFile.source_location == "build.gradle"
            def runtimeClasspathResolved = runtimeClasspath.resolved as Map
            def testFoo = runtimeClasspathResolved[fooGav] as Map
            verifyAll(testFoo) {
                package_url == this.fooPurl
                relationship == "indirect"
                dependencies == []
            }
            def testIncludedChild = runtimeClasspathResolved["project :included-child"] as Map
            verifyAll(testIncludedChild) {
                package_url == "pkg:maven/org.test.included/included-child@1.0"
                relationship == "direct"
                dependencies == [this.fooGav]
            }
        }
        if (resolveIncludedBuild) {
            def includedChildRuntimeClasspath = jsonManifest("project :included-child")
            def includedChildRuntimeFile = includedChildRuntimeClasspath.file as Map
            verifyAll {
                includedChildRuntimeFile.source_location == "included-child/build.gradle"
                def includedChildRuntimeClasspathResolved = includedChildRuntimeClasspath.resolved as Map

                def testFoo = includedChildRuntimeClasspathResolved[fooGav] as Map
                verifyAll(testFoo) {
                    package_url == this.fooPurl
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
