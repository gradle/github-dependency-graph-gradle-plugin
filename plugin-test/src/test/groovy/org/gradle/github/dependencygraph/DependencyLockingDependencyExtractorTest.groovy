package org.gradle.github.dependencygraph

import org.gradle.test.fixtures.PluginPublisher
import org.gradle.test.fixtures.maven.MavenModule
import spock.lang.IgnoreIf

class DependencyLockingDependencyExtractorTest extends BaseExtractorTest {
    private MavenModule foo
    private MavenModule bar
    private MavenModule baz
    private File settingsFile
    private File buildFile

    def setup() {
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

    def "extracts dependencies when dependency locking is enabled"() {
        given:
        buildFile << """
        dependencies {
            implementation "org.test:foo:+"
        }
        
        dependencyLocking {
            lockAllConfigurations()
            lockMode = LockMode.STRICT
        }
        """

        // Write dependency lock file
        run("dependencies", "--write-locks")
        mavenRepo.module("org.test", "foo", "1.1").publish()

        when:
        applyDependencyGraphPlugin()
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"

        manifest.assertResolved([
            "org.test:foo:1.0": [
                package_url: purlFor(foo)
            ]
        ])
    }
}
