package org.gradle.github.dependencygraph

import org.apache.commons.io.FileUtils
import spock.lang.IgnoreIf

@IgnoreIf({ System.getProperty("testGradleVersion") == "5.6.4" })
// Samples aren't designed to run on Gradle 5.x
class SampleProjectDependencyExtractorTest extends BaseExtractorTest {
    def setup() {
        applyDependencyGraphPlugin()
        establishEnvironmentVariables()
    }

    def "check single-project sample"() {
        def sampleDir = new File("../sample-projects/java-single-project")

        FileUtils.copyDirectory(sampleDir, testDirectory)

        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        def manifestDependencies = manifest.resolved

        [   // plugin dependencies
            "com.gradle.enterprise:com.gradle.enterprise.gradle.plugin:3.12.6",
            "com.gradle:gradle-enterprise-gradle-plugin:3.12.6",
            "com.diffplug.spotless:spotless-plugin-gradle:4.5.1",
            "com.diffplug.durian:durian-core:1.2.0",
        ].forEach {
            assert manifestDependencies.containsKey(it)
            assert (manifestDependencies[it].package_url as String).endsWith("?repository_url=https%3A%2F%2Fplugins.gradle.org%2Fm2")
        }

        [   // regular dependencies
            "org.apache.commons:commons-math3:3.6.1", // 'api' dependency
            "com.google.guava:guava:31.1-jre", // 'implementation' dependency
            "com.google.guava:failureaccess:1.0.1", // transitive 'implementation' dependency
            "org.junit.jupiter:junit-jupiter:5.9.1" // testImplementation dependency
        ].forEach {
            assert manifestDependencies.containsKey(it)
            assert !(manifestDependencies[it].package_url as String).contains("?repository_url") // Maven repo default is not included
        }
    }

    def "check java-multi-project sample"() {
        def sampleDir = new File("../sample-projects/java-multi-project")

        FileUtils.copyDirectory(sampleDir, testDirectory)

        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.resolved.keySet().containsAll([
            "org.apache.commons:commons-math3:3.5",
            "org.apache.commons:commons-math3:3.6.1", // buildSrc dependency with different version
            "org.apache.commons:commons-text:1.9",
            "org.apache.commons:commons-lang3:3.11",
            "org.junit.jupiter:junit-jupiter:5.9.1"
        ])
    }

    // Temporarily disable test that hangs on Gradle < 7.6
    // TODO: Re-enable this test
    @IgnoreIf({ System.getProperty("testGradleVersion") != null })
    def "check java-included-builds sample"() {
        def sampleDir = new File("../sample-projects/java-included-builds")

        FileUtils.copyDirectory(sampleDir, testDirectory)

        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle.kts"
        manifest.resolved.keySet().containsAll([
            "org.slf4j:slf4j-api:2.0.6",
            "org.apache.commons:commons-text:1.9",
            "org.apache.commons:commons-lang3:3.11",
            "org.junit.jupiter:junit-jupiter:5.9.1"
        ])
    }
}
