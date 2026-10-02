package org.gradle.github.dependencygraph

import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf

class StrictDependencyResolutionTest extends BaseExtractorTest {
    private MavenModule foo
    private MavenModule bar
    private File buildFile

    def setup() {
        applyDependencyGraphPlugin()
        establishEnvironmentVariables()

        bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        foo = mavenRepo.module("org.test", "foo", "1.0").dependsOn(bar).publish()
        file("settings.gradle") << "rootProject.name = 'a'"
        buildFile = file("build.gradle") << """
            apply plugin: 'java'
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                implementation 'org.test:foo:1.0'
            }
        """
    }

    def "preserves lenient collection when strict mode is #setting"() {
        given:
        buildFile << """
            dependencies {
                implementation 'org.test:missing:1.0'
            }
        """
        if (setting != null) {
            executer.withArgument("-DDEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED=$setting")
        }

        when:
        run()

        then:
        gitHubManifest().assertResolved(["org.test:foo:1.0", "org.test:bar:1.0"])

        where:
        setting << [null, "false"]
    }

    def "strict collection rejects an unresolved #kind dependency without a snapshot"() {
        given:
        def dependency = "org.test:missing:1.0"
        if (kind == "transitive") {
            mavenRepo.module("org.test", "broken", "1.0")
                .dependsOn("org.test", "missing", "1.0").publish()
            dependency = "org.test:broken:1.0"
        } else if (kind == "plugin marker") {
            mavenRepo.module("org.test.plugin", "org.test.plugin.gradle.plugin", "1.0")
                .dependsOn("org.test", "missing", "1.0").publish()
            dependency = "org.test.plugin:org.test.plugin.gradle.plugin:1.0"
        }
        buildFile << """
            dependencies {
                implementation '$dependency'
            }
        """
        executer.withArgument("-DDEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED=true")

        when:
        def result = runAndFail()

        then:
        result.output.contains("org.test:missing:1.0")
        result.output.contains("compileClasspath")
        !dependencyGraphFile.exists()

        where:
        kind << ["direct", "transitive", "plugin marker"]
    }

    def "strict collection can be enabled by environment variable"() {
        given:
        buildFile << """
            dependencies {
                implementation 'org.test:missing:1.0'
            }
        """
        executer.withEnvironmentVars(environmentVars.asEnvironmentMap() +
            ["DEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED": "true"])

        when:
        def result = runAndFail()

        then:
        result.output.contains("org.test:missing:1.0")
        !dependencyGraphFile.exists()
    }

    def "system property can disable strict collection enabled by environment"() {
        given:
        buildFile << """
            dependencies {
                implementation 'org.test:missing:1.0'
            }
        """
        executer.withEnvironmentVars(environmentVars.asEnvironmentMap() +
            ["DEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED": "true"])
        executer.withArgument("-DDEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED=false")

        when:
        run()

        then:
        gitHubManifest().assertResolved(["org.test:foo:1.0", "org.test:bar:1.0"])
    }

    def "rejects an invalid strict collection setting #setting without a snapshot"() {
        given:
        executer.withArgument("-DDEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED=$setting")

        when:
        def result = runAndFail()

        then:
        result.output.contains("DEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED")
        result.output.contains("true")
        result.output.contains("false")
        !dependencyGraphFile.exists()

        where:
        setting << ["tru", ""]
    }

    def "strict collection rejects unresolved dependencies during a custom task"() {
        given:
        buildFile << """
            dependencies {
                implementation 'org.test:missing:1.0'
            }
            tasks.register('resolveGraph') {
                doLast {
                    configurations.compileClasspath.incoming.resolutionResult.allDependencies
                }
            }
        """
        executer.withArgument("-DDEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED=true")

        when:
        def result = runAndFail("resolveGraph")

        then:
        result.output.contains("org.test:missing:1.0")
        !dependencyGraphFile.exists()
    }

    def "rejects an invalid strict collection setting in a project without dependencies"() {
        given:
        buildFile.text = "apply plugin: 'java'"
        executer.withArgument("-DDEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED=tru")

        when:
        def result = runAndFail()

        then:
        result.output.contains("DEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED")
        !dependencyGraphFile.exists()
    }

    def "strict collection rejects unresolved dependencies in an included build"() {
        given:
        file("settings.gradle") << "\nincludeBuild 'included'"
        file("included/settings.gradle") << "rootProject.name = 'included'"
        file("included/build.gradle") << """
            apply plugin: 'java'
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                implementation 'org.test:missing:1.0'
            }
        """
        executer.withArgument("-DDEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED=true")

        when:
        def result = runAndFail()

        then:
        result.output.contains("org.test:missing:1.0")
        result.output.contains(":included")
        !dependencyGraphFile.exists()
    }

    def "strict collection preserves resolved direct and transitive packages"() {
        given:
        executer.withArgument("-DDEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED=true")

        when:
        run()

        then:
        gitHubManifest().assertResolved([
            "org.test:foo:1.0": [
                package_url: purlFor(foo),
                dependencies: ["org.test:bar:1.0"]
            ],
            "org.test:bar:1.0": [
                package_url: purlFor(bar),
                relationship: "indirect"
            ]
        ])
    }

    @IgnoreIf({ GradleVersion.version(testGradleVersion) < GradleVersion.version("8.1") })
    def "strict collection preserves configuration-cache behavior"() {
        given:
        executer.withArgument("-DDEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED=true")
        executer.withArgument("--configuration-cache")

        when:
        run()

        then:
        gitHubManifest().assertResolved(["org.test:foo:1.0", "org.test:bar:1.0"])

        when:
        dependencyGraphFile.delete()
        def result = run()

        then:
        result.output.contains("Gradle build state was reused from the configuration-cache")
        !dependencyGraphFile.exists()
    }

    def "strict collection ignores unresolved dependencies in an excluded configuration"() {
        given:
        buildFile << """
            configurations {
                optional
            }
            dependencies {
                optional 'org.test:missing:1.0'
            }
            tasks.register('resolveOptional') {
                doLast {
                    configurations.optional.incoming.resolutionResult.allDependencies
                }
            }
        """
        executer.withArgument("-DDEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED=true")
        executer.withArgument("-DDEPENDENCY_GRAPH_EXCLUDE_CONFIGURATIONS=optional")

        when:
        run("resolveOptional", "ForceDependencyResolutionPlugin_resolveAllDependencies")

        then:
        gitHubManifest().assertResolved(["org.test:foo:1.0", "org.test:bar:1.0"])
    }

    def "strict collection ignores unresolved dependencies in an excluded project"() {
        given:
        file("settings.gradle") << "\ninclude 'optional'"
        file("optional/build.gradle") << """
            apply plugin: 'java'
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                implementation 'org.test:missing:1.0'
            }
        """
        executer.withArgument("-DDEPENDENCY_GRAPH_FAIL_ON_UNRESOLVED=true")
        executer.withArgument("-DDEPENDENCY_GRAPH_EXCLUDE_PROJECTS=:optional")

        when:
        run()

        then:
        gitHubManifest().assertResolved(["org.test:foo:1.0", "org.test:bar:1.0"])
    }
}
