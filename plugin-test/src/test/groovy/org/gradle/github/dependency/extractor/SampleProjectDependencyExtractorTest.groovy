package org.gradle.github.dependency.extractor

import org.apache.commons.io.FileUtils
import org.gradle.github.dependency.base.BaseExtractorTest
import spock.lang.IgnoreIf

@IgnoreIf({ System.getProperty("testGradleVersion") == "5.6.4"})
// Samples aren't designed to run on Gradle 5.x
class SampleProjectDependencyExtractorTest extends BaseExtractorTest {
    def setup() {
        applyExtractorPlugin()
        establishEnvironmentVariables()
    }

    def "check single-project sample"() {
        def sampleDir = new File("../sample-projects/java-single-project")

        FileUtils.copyDirectory(sampleDir, testDirectory)

        when:
        succeeds("build")

        then:
        resolvedConfigurations == [
                "project : [annotationProcessor]",
                "project : [compileClasspath]",
                "project : [testAnnotationProcessor]",
                "project : [testCompileClasspath]",
                "project : [testRuntimeClasspath]"
        ]
    }

    def "check java-multi-project sample"() {
        def sampleDir = new File("../sample-projects/java-multi-project")

        FileUtils.copyDirectory(sampleDir, testDirectory)

        when:
        succeeds("build")

        then:
        resolvedConfigurations == [
                "project :app [annotationProcessor]",
                "project :app [compileClasspath]",
                "project :app [runtimeClasspath]",
                "project :app [testAnnotationProcessor]",
                "project :app [testCompileClasspath]",
                "project :app [testRuntimeClasspath]",
                "project :buildSrc [annotationProcessor]",
                "project :buildSrc [buildScriptClasspath]",
                "project :buildSrc [compileClasspath]",
                "project :list [annotationProcessor]",
                "project :list [compileClasspath]",
                "project :list [testAnnotationProcessor]",
                "project :list [testCompileClasspath]",
                "project :list [testRuntimeClasspath]",
                "project :utilities [annotationProcessor]",
                "project :utilities [compileClasspath]"
        ]
    }

    def "check java-included-builds sample"() {
        def sampleDir = new File("../sample-projects/java-included-builds")

        FileUtils.copyDirectory(sampleDir, testDirectory)

        when:
        succeeds("build")

        then:
        resolvedConfigurations == [
                "project :app [annotationProcessor]",
                "project :app [classpath]",
                "project :app [compileClasspath]",
                "project :app [runtimeClasspath]",
                "project :app [testAnnotationProcessor]",
                "project :app [testCompileClasspath]",
                "project :app [testRuntimeClasspath]",
                "project :build-logic [annotationProcessor]",
                "project :build-logic [classpath]",
                "project :build-logic [compileClasspath]",
                "project :list [annotationProcessor]",
                "project :list [classpath]",
                "project :list [compileClasspath]",
                "project :utilities [annotationProcessor]",
                "project :utilities [classpath]",
                "project :utilities [compileClasspath]",
                "project :utilities [testAnnotationProcessor]",
                "project :utilities [testCompileClasspath]",
                "project :utilities [testRuntimeClasspath]"
        ]
    }

    private List<String> getResolvedConfigurations() {
        Set<String> resolved = jsonManifests().keySet()
        return resolved
                // `buildSrc` resolution changed in Gradle 8.x
                .collect { it == "project :buildSrc [runtimeClasspath]"
                            ? "project :buildSrc [buildScriptClasspath]"
                            : it }
                .sort()
    }
}
