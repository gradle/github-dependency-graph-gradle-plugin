package org.gradle.github.dependencygraph

import org.gradle.test.fixtures.PluginPublisher
import org.gradle.test.fixtures.maven.MavenModule
import spock.lang.IgnoreIf

class SingleProjectDependencyExtractorTest extends BaseExtractorTest {
    private MavenModule foo
    private MavenModule bar
    private MavenModule baz
    private File settingsFile
    private File buildFile

    def setup() {
        applyDependencyGraphPlugin()
        establishEnvironmentVariables()

        foo = mavenRepo.module("org.test", "foo", "1.0").publish()
        bar = mavenRepo.module("org.test", "bar", "1.0").publish()
        baz = mavenRepo.module("org.test", "baz", "1.0").dependsOn(bar).publish()

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

    def "extracts implementation and test dependencies for a java project"() {
        given:
        buildFile << """
        dependencies {
            implementation "org.test:foo:1.0"
            implementation "org.test:bar:1.0"
            testImplementation "org.test:baz:1.0"
        }
        """

        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"

        manifest.assertResolved([
            "org.test:foo:1.0": [
                package_url: purlFor(foo)
            ],
            "org.test:bar:1.0": [
                package_url: purlFor(bar)
            ],
            "org.test:baz:1.0": [
                package_url : purlFor(baz),
                dependencies: ["org.test:bar:1.0"]
            ]
        ])
    }

    def "extracts only those dependencies resolved during project execution"() {
        given:
        buildFile << """
        dependencies {
            implementation "org.test:foo:1.0"
            testImplementation "org.test:bar:1.0"
        }
        """

        when:
        run("dependencies", "--configuration", "runtimeClasspath")

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"

        manifest.assertResolved([
            "org.test:foo:1.0": [package_url: purlFor(foo)]
        ])
    }

    def "extracts dependencies from custom configuration"() {
        given:
        buildFile << """
        configurations {
            custom
        }
        dependencies {
            custom "org.test:foo:1.0"
            custom "org.test:bar:1.0"
        }
        """
        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"

        manifest.assertResolved([
            "org.test:foo:1.0": [
                package_url: purlFor(foo)
            ],
            "org.test:bar:1.0": [
                package_url: purlFor(bar)
            ]
        ])
    }

    def "extracts transitive dependencies"() {
        given:
        def foo2 = mavenRepo.module("org.test", "foo", "2.0").dependsOn(bar).publish()
        buildFile << """
        dependencies {
            implementation "org.test:foo:2.0"
        }
        """
        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"

        manifest.assertResolved([
            "org.test:foo:2.0": [
                package_url : purlFor(foo2),
                dependencies: ["org.test:bar:1.0"]
            ],
            "org.test:bar:1.0": [
                package_url : purlFor(bar),
                relationship: "indirect"
            ]
        ])
    }

    def "extracts direct dependency for transitive dependency updated by constraint"() {
        given:
        def bar11 = mavenRepo.module("org.test", "bar", "1.1").publish()
        buildFile << """
        dependencies {
            implementation "org.test:baz:1.0"
            
            constraints {
                implementation "org.test:bar:1.1"
            }
        }
        """
        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "org.test:baz:1.0": [
                package_url : purlFor(baz),
                dependencies: ["org.test:bar:1.1"]
            ],
            "org.test:bar:1.1": [
                package_url : purlFor(bar11),
                relationship: "direct" // Constraint creates a direct dependency relationship
            ]
        ])
    }

    def "extracts both versions from build with two versions of the same dependency"() {
        given:
        def bar11 = mavenRepo.module("org.test", "bar", "1.1").publish()
        buildFile << """
        dependencies {
            implementation "org.test:bar:1.0"
            testImplementation "org.test:bar:1.1"
        }
        """
        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"

        manifest.assertResolved([
            "org.test:bar:1.0": [
                package_url: purlFor(bar)
            ],
            "org.test:bar:1.1": [
                package_url: purlFor(bar11)
            ]
        ])
    }

    def "extracts both versions from build with two versions of the same transitive dependency"() {
        given:
        def bar11 = mavenRepo.module("org.test", "bar", "1.1").publish()
        buildFile << """
        configurations {
            testCompileClasspath {
                resolutionStrategy.force("org.test:bar:1.1")
            }
        }
        dependencies {
            implementation "org.test:baz:1.0"
        }
        """
        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"

        manifest.assertResolved([
            "org.test:baz:1.0": [
                package_url : purlFor(baz),
                dependencies: ["org.test:bar:1.0", "org.test:bar:1.1"]
            ],
            "org.test:bar:1.0": [
                package_url : purlFor(bar),
                relationship: "indirect"
            ],
            "org.test:bar:1.1": [
                package_url : purlFor(bar11),
                relationship: "indirect"
            ]
        ])
    }

    def "merges transitive dependencies with different resolution scopes"() {

        def transitiveDep = mavenRepo.module("org.test", "transitive", "1.0")
            .dependsOn(foo)
            .dependsOn([scope: "runtime"], bar).publish()
        def directDep = mavenRepo.module("org.test", "direct", "1.0").dependsOn(transitiveDep).publish()

        given:
        buildFile << """
            apply plugin: 'java-library'
            dependencies {
                api 'org.test:direct:1.0'
            }
            tasks.register("resolveCompileClasspath") {
              doLast {
                println(configurations.compileClasspath.files())
              }
            }
            tasks.register("resolveRuntimeClasspath") {
              doLast {
                println(configurations.runtimeClasspath.files())
              }
            }
        """

        when:
        run("resolveCompileClasspath", "resolveRuntimeClasspath")

        then:
        def manifestA = gitHubManifest()
        manifestA.sourceFile == "settings.gradle"
        manifestA.assertResolved([
            "org.test:direct:1.0"    : [package_url: (purlFor(directDep)),
                                        dependencies: ["org.test:transitive:1.0"]],
            "org.test:transitive:1.0": [package_url: (purlFor(transitiveDep)),
                                        dependencies: ["org.test:foo:1.0", "org.test:bar:1.0"],
                                        relationship: "indirect"],
            "org.test:foo:1.0"       : [package_url: purlFor(foo),
                                        relationship: "indirect"],
            "org.test:bar:1.0"       : [package_url: purlFor(bar),
                                        relationship: "indirect"]
        ])
    }


    def "extracts direct and transitive dependencies from buildscript"() {
        given:
        buildFile << """
            buildscript {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                
                dependencies {
                    classpath "org.test:baz:1.0"
                }        
            }
        """

        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"

        manifest.assertResolved([
            "org.test:baz:1.0": [
                package_url : purlFor(baz),
                dependencies: ["org.test:bar:1.0"]
            ],
            "org.test:bar:1.0": [
                package_url : purlFor(bar),
                relationship: "indirect"
            ]
        ])
    }

    def "extracts buildscript dependencies from settings script"() {
        given:
        settingsFile << """
            buildscript {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                dependencies {
                    classpath "org.test:baz:1.0"
                }
            }
        """

        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"

        manifest.assertResolved([
            "org.test:baz:1.0": [
                package_url : purlFor(baz),
                dependencies: ["org.test:bar:1.0"]
            ],
            "org.test:bar:1.0": [
                package_url : purlFor(bar),
                relationship: "indirect"
            ]
        ])
    }

    def "extracts project plugin dependency"() {
        given:
        new PluginPublisher(mavenRepo, testDirectory).publishProjectPlugin("plugin", "my.project.plugin")

        settingsFile.text = """
            pluginManagement {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            }
        """ + settingsFile.text
        buildFile.text = """
            plugins {
                id("my.project.plugin") version("1.0")    
            }
        """ + buildFile.text

        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "my.project.plugin:my.project.plugin.gradle.plugin:1.0": [
                package_url : purlFor("my.project.plugin", "my.project.plugin.gradle.plugin", "1.0"),
                dependencies: ["com.example:plugin:1.0"]
            ],
            "com.example:plugin:1.0"                               : [
                package_url : purlFor("com.example", "plugin", "1.0"),
                relationship: "indirect"
            ]
        ])
    }

    @IgnoreIf({
        !settingsPluginsAreSupported()
    })
    def "extracts settings plugin dependency"() {
        given:
        new PluginPublisher(mavenRepo, testDirectory).publishSettingsPlugin("plugin", "my.settings.plugin")

        settingsFile.text = """
            pluginManagement {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            }
            plugins {
                id("my.settings.plugin") version("1.0")
            }
        """ + settingsFile.text

        when:
        run()

        then:
        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved([
            "my.settings.plugin:my.settings.plugin.gradle.plugin:1.0": [
                package_url : purlFor("my.settings.plugin", "my.settings.plugin.gradle.plugin", "1.0"),
                dependencies: ["com.example:plugin:1.0"]
            ],
            "com.example:plugin:1.0"                                 : [
                package_url : purlFor("com.example", "plugin", "1.0"),
                relationship: "indirect"
            ]
        ])
    }
}
