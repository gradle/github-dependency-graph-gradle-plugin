package org.gradle.github.dependencygraph

import spock.lang.IgnoreIf

class DependencyExtractorManifestFileLocationTest extends BaseExtractorTest {

    def setup() {
        applyDependencyGraphPlugin()
        establishEnvironmentVariables()
    }

    def "uses settings.gradle.kts file as manifest file location"() {
        given:
        file("settings.gradle.kts") << """
            rootProject.name = "a"
            
            include("b", "c")
        """

        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle.kts"
    }

    def "uses root build file as manifest file location if settings file does not exist"() {
        given:
        file("build.gradle.kts") << """
        """

        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "build.gradle.kts"
    }

    // Newer Gradle versions will fail if neither settings nor root build file exists
    @IgnoreIf({ enforcesEitherSettingsOrBuildFile() })
    def "uses non-existent build file as manifest file location if settings and root build files do not exist"() {
        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "build.gradle"
    }

}
