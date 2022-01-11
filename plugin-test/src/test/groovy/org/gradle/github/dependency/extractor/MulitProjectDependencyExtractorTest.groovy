package org.gradle.github.dependency.extractor

import org.gradle.github.dependency.extractor.base.BaseExtractorTest
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
        def parentRuntimeClasspath = jsonManifest(configuration: "runtimeClasspath")
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
            def runtimeClasspath = jsonManifest(project: ':' + name, configuration: "runtimeClasspath")
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
        def aRuntimeClasspathFile = aRuntimeClasspath.file as Map
        aRuntimeClasspathFile.source_location == "a/build.gradle"
        def aClasspathResolved = aRuntimeClasspath.resolved as Map
        def aTestFoo = aClasspathResolved[fooPurl] as Map
        verifyAll(aTestFoo) {
            purl == this.fooPurl
            relationship == "direct"
            dependencies == []
        }
        def bRuntimeClasspath = jsonManifest(project: ":b", configuration: "runtimeClasspath")
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
        def buildSrcRuntimeFile = buildSrcRuntimeClasspath.file as Map
        buildSrcRuntimeFile.source_location == "buildSrc/build.gradle"
        def buildSrcRuntimeClasspathResolved = buildSrcRuntimeClasspath.resolved as Map
        def testFoo = buildSrcRuntimeClasspathResolved[fooPurl] as Map
        verifyAll(testFoo) {
            purl == this.fooPurl
            relationship == "direct"
            dependencies == []
        }
        def runtimeClasspath = jsonManifest(configuration: "runtimeClasspath")
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

    def "project leveraging included builds"() {
        given:
        multiProjectBuild("parent", []) {
            project("included-child").tap {
                buildFile """
                apply plugin: 'java'
                
                group = 'org.test.included'
                version = '1.0'
                dependencies {
                    implementation 'org.test-published:foo:1.0'    
                }
                """
                setupBuildFile(it)
                file("src/main/java/Dummy.java") << "public class Dummy {}"
                settingsFile << "rootProject.name = '$projectName'"
            }
            settingsFile << "includeBuild 'included-child'"

            buildFile """
            apply plugin: 'java'
            
            dependencies {
                implementation 'org.test.included:included-child'    
            }
            """
            setupBuildFile(it)
        }
        when:
        succeeds("validate")

        then:
        def runtimeClasspath = jsonManifest(configuration: "runtimeClasspath")
        def runtimeFile = runtimeClasspath.file as Map
        runtimeFile.source_location == "build.gradle"
    }
}
