package org.gradle.github.dependencygraph

import org.gradle.test.fixtures.maven.MavenModule

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
            .withArgument("-Dorg.gradle.github.env.GITHUB_DEPENDENCY_GRAPH_JOB_CORRELATOR=TEST_CORRELATOR")
            .withArgument("-Dorg.gradle.github.env.GITHUB_REF=refs/my-branch/foo")
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
}
