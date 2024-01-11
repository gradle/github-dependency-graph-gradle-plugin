package org.gradle.github.dependencygraph

import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf

class DependencyExtractorConfigTest extends BaseExtractorTest {
    private MavenModule foo
    private File settingsFile
    private File buildFile

    def setup() {
        applyDependencyGraphPlugin()
        establishEnvironmentVariables()

        foo = mavenRepo.module("org.test", "foo", "1.0").publish()

        settingsFile = file("settings.gradle") << """
            rootProject.name = 'a'    
        """

        buildFile = file("build.gradle") << """
            apply plugin: 'java'

            repositories {
                maven { url "${mavenRepo.uri}" }
            }            
        """
    }

    def "can override env vars with system properties"() {
        given:
        buildFile << """
        dependencies {
            implementation "org.test:foo:1.0"
        }
        """

        when:
        executer
            .withArgument("-DGITHUB_DEPENDENCY_GRAPH_JOB_CORRELATOR=TEST_CORRELATOR")
            .withArgument("-DGITHUB_DEPENDENCY_GRAPH_REF=refs/my-branch/foo")
        run()

        then:
        def manifestFile = reportDir.file("TEST_CORRELATOR.json")
        def snapshot = new JsonRepositorySnapshotLoader(manifestFile).jsonRepositorySnapshot()
        assert snapshot.sha == environmentVars.sha
        assert snapshot.ref == "refs/my-branch/foo"
        def job = snapshot.job as Map
        assert job.correlator == "TEST_CORRELATOR"
        assert job.id == environmentVars.jobId
    }

    @IgnoreIf({
        // There is an issue where BuildService is closed too early in Gradle 8.0,
        // resulting in empty dependency graph.
        GradleVersion.version(testGradleVersion) < GradleVersion.version("8.1")
    })
    def "is compatible with configuration-cache for Gradle 8.1+"() {
        given:
        buildFile << """
        dependencies {
            implementation "org.test:foo:1.0"
        }
        """

        when:
        executer.withArgument("--configuration-cache")
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"

        manifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)]
        ])

        // Execute again to test loading from config-cache
        when:
        dependencyGraphFile.delete()
        executer.withArgument("--configuration-cache")
        def buildResult = run()

        then:
        buildResult.output.contains("Gradle build state was reused from the configuration-cache: Dependency Graph file will not be generated.")
        !dependencyGraphFile.exists()
    }

    def "fails gracefully if configuration values not set"() {
        when:
        def envVars = environmentVars.asEnvironmentMap()
        envVars.remove("GITHUB_DEPENDENCY_GRAPH_JOB_CORRELATOR")
        executer.withEnvironmentVars(envVars)
        def result = executer.runWithFailure()

        then:
        result.output.contains("'GITHUB_DEPENDENCY_GRAPH_JOB_CORRELATOR' must be set")
    }
}
