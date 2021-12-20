package org.gradle.github.dependency.extractor

import org.gradle.integtests.fixtures.build.BuildTestFile

class MulitProjectDependencyExtractorTest extends BaseExtractorTest {
    def setup() {
        applyExtractorPlugin()
    }

    def "multiple projects with single dependency each"() {
        given:
        def foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        def fooPurl = purlFor(foo)
        List<String> subprojects = ["a", "b"]
        multiProjectBuild("parent", subprojects) {
            List<BuildTestFile> projects = subprojects.collect { project(it) }.plus(this)
            projects.forEach {
                it.buildFile """
                apply plugin: 'java'
                dependencies {
                    implementation 'org.test:foo:1.0'    
                }
                """
                it.buildFile """
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
        }

        when:
        executer.startBuildProcessInDebugger(true)
        succeeds("validate")

        then:
        def manifests = jsonManifests()
        manifests.size() == 3
        def parentRuntimeClasspath = manifests["::runtimeClasspath"]
        parentRuntimeClasspath.name == "::runtimeClasspath"
        def parentClasspathResolved = parentRuntimeClasspath.resolved as Map
        def parentTestFoo = parentClasspathResolved[fooPurl] as Map
        verifyAll(parentTestFoo) {
            purl == fooPurl
            relationship == "direct"
            dependencies == []
        }
        subprojects.each {name ->
            def runtimeClasspath = manifests[":$name:runtimeClasspath"]
            runtimeClasspath.name == ":$name:runtimeClasspath"
            def classpathResolved = runtimeClasspath.resolved as Map
            def testFoo = classpathResolved[fooPurl] as Map
            verifyAll(testFoo) {
                purl == fooPurl
                relationship == "direct"
                dependencies == []
            }
        }
    }
}
