package org.gradle.github.dependency.extractor

import org.apache.commons.io.FileUtils
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
        run()

        then:
        manifestNames == ["build :", "project :"]
        def buildManifest = jsonManifest("build :")
        def buildDependencies = (buildManifest.resolved as Map).keySet()
        buildDependencies.containsAll([
                "com.gradle.enterprise:com.gradle.enterprise.gradle.plugin:3.12.6"
        ])

        def projectManifest = jsonManifest("project :")
        def projectDependencies = (projectManifest.resolved as Map).keySet()
        projectDependencies.containsAll([
                "com.diffplug.spotless:spotless-plugin-gradle:4.5.1", // 'plugins' dependency
                "com.diffplug.durian:durian-core:1.2.0", // transitive 'plugins' dependency
                "org.apache.commons:commons-math3:3.6.1", // 'api' dependency
                "com.google.guava:guava:31.1-jre", // 'implementation' dependency
                "com.google.guava:failureaccess:1.0.1", // transitive 'implementation' dependency
                "org.junit.jupiter:junit-jupiter:5.9.1" // testImplementation dependency
        ])
    }

    def "check java-multi-project sample"() {
        def sampleDir = new File("../sample-projects/java-multi-project")

        FileUtils.copyDirectory(sampleDir, testDirectory)

        when:
        run()

        then:
        manifestNames == [
                "project :app",
                "project :buildSrc",
                "project :list",
                "project :utilities"
        ]
    }

    def "check java-included-builds sample"() {
        def sampleDir = new File("../sample-projects/java-included-builds")

        FileUtils.copyDirectory(sampleDir, testDirectory)

        when:
        run()

        then:
        manifestNames == [
                "project :app",
                "project :build-logic",
                "project :list",
                "project :utilities"
        ]
    }
}
