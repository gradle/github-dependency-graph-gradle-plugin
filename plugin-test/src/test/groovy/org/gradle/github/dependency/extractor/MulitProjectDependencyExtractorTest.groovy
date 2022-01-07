package org.gradle.github.dependency.extractor

import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.compatibility.MultiVersionTest
import org.gradle.test.fixtures.maven.MavenModule

@MultiVersionTest
class MulitProjectDependencyExtractorTest extends BaseExtractorTest {

    private MavenModule foo
    private String fooPurl
    private MavenModule bar
    private String barPurl

    def setup() {
        applyExtractorPlugin()

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
        def parentRuntimeClasspath = jsonManifest(configuration: "runtimeClasspath")
        def parentClasspathResolved = parentRuntimeClasspath.resolved as Map
        def parentTestFoo = parentClasspathResolved[fooPurl] as Map
        verifyAll(parentTestFoo) {
            purl == this.fooPurl
            relationship == "direct"
            dependencies == []
        }
        subprojects.each { name ->
            def runtimeClasspath = jsonManifest(project: ':' + name, configuration: "runtimeClasspath")
            def classpathResolved = runtimeClasspath.resolved as Map
            def testFoo = classpathResolved[fooPurl] as Map
            verifyAll(testFoo) {
                purl == this.fooPurl
                relationship == "direct"
                dependencies == []
            }
        }
    }

    def "multi-project build where one project depends upon another"() {
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
        succeeds("validate")

        then:
        def manifests = jsonManifests()
        manifests.size() == 2
        def aRuntimeClasspath = jsonManifest(project: ":a", configuration: "runtimeClasspath")
        def aClasspathResolved = aRuntimeClasspath.resolved as Map
        def aTestFoo = aClasspathResolved[fooPurl] as Map
        verifyAll(aTestFoo) {
            purl == this.fooPurl
            relationship == "direct"
            dependencies == []
        }
        def bRuntimeClasspath = jsonManifest(project: ":b", configuration: "runtimeClasspath")
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
    }

    def "project with buildSrc"() {
        given:
        multiProjectBuild("parent", []) {
            project("buildSrc").tap {
                buildFile """
                apply plugin: 'java'
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
        def buildSrcRuntimeClasspath =
                jsonManifest(build: ":buildSrc", configuration: "runtimeClasspath")
        def buildSrcRuntimeClasspathResolved = buildSrcRuntimeClasspath.resolved as Map
        def testFoo = buildSrcRuntimeClasspathResolved[fooPurl] as Map
        verifyAll(testFoo) {
            purl == this.fooPurl
            relationship == "direct"
            dependencies == []
        }
        def runtimeClasspath = jsonManifest(configuration: "runtimeClasspath")
        def runtimeClasspathResolved = runtimeClasspath.resolved as Map
        def testBar = runtimeClasspathResolved[barPurl] as Map
        verifyAll(testBar) {
            purl == this.barPurl
            relationship == "direct"
            dependencies == []
        }
    }
}
