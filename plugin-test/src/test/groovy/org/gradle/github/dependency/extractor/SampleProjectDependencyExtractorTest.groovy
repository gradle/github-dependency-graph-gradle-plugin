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
        manifestNames == ["project :"]
    }

    def "check java-multi-project sample"() {
        def sampleDir = new File("../sample-projects/java-multi-project")

        FileUtils.copyDirectory(sampleDir, testDirectory)

        when:
        succeeds("build")

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
        succeeds("build")

        then:
        manifestNames == [
                "project :app",
                "project :build-logic",
                "project :list",
                "project :utilities"
        ]
    }

    private List<String> getManifestNames() {
        return jsonManifests().keySet() as List
    }
}
