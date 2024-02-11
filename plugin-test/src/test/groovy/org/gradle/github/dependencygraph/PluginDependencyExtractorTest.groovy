package org.gradle.github.dependencygraph

import org.gradle.test.fixtures.PluginPublisher
import org.gradle.test.fixtures.file.TestFile

class PluginDependencyExtractorTest extends BaseExtractorTest {
    private static final SETTINGS_PLUGIN = "my.settings.plugin"
    private static final PROJECT_PLUGIN_1 = "my.project.plugin1"
    private static final PROJECT_PLUGIN_2 = "my.project.plugin2"

    private File settingsFile
    private File buildFile

    def setup() {
        applyDependencyGraphPlugin()
        establishEnvironmentVariables()

        mavenRepo.module("org.test", "foo", "1.0").publish()
        mavenRepo.module("org.test", "bar", "1.0").publish()

        def pluginPublisher = new PluginPublisher(mavenRepo, testDirectory)
        pluginPublisher.publishSettingsPlugin("settingPlugin", SETTINGS_PLUGIN, "implementation 'org.test:foo:1.0'")
        pluginPublisher.publishProjectPlugin("plugin1", PROJECT_PLUGIN_1)
        pluginPublisher.publishProjectPlugin("plugin2", PROJECT_PLUGIN_2, "implementation 'org.test:bar:1.0'")

        settingsFile = file("settings.gradle")
        buildFile = file("build.gradle")
    }

    def "extracts all plugin dependencies from multi project build"() {
        given:
        createMultiProjectBuildWithPlugins(testDirectory)

        when:
        run()

        then:
        manifestHasPlugins()
    }

    def "extracts all plugin dependencies from multi project buildSrc build"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << "apply plugin: 'java'"

        createMultiProjectBuildWithPlugins(file("buildSrc"))

        when:
        run()

        then:
        manifestHasPlugins()
    }

    def "extracts all plugin dependencies from multi project included build"() {
        given:
        settingsFile << "includeBuild 'included-child'"
        buildFile << "apply plugin: 'java'"

        createMultiProjectBuildWithPlugins(file("included-child"))

        when:
        run()

        then:
        manifestHasPlugins()
    }

    def "extracts all plugin dependencies from multi project included plugin build"() {
        given:
        settingsFile << """
            pluginManagement {
                includeBuild 'included-plugin'
            }
        """
        buildFile << "apply plugin: 'java'"

        createMultiProjectBuildWithPlugins(file("included-plugin"))

        when:
        run()

        then:
        manifestHasPlugins()
    }

    private void createMultiProjectBuildWithPlugins(TestFile rootDir) {
        def settingsPluginDeclaration = settingsPluginsAreSupported() ? """
            plugins {
                id("$SETTINGS_PLUGIN") version("1.0")
            }
        """ : ""

        rootDir.file("settings.gradle") << """
            pluginManagement {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            }
            $settingsPluginDeclaration
            include 'a', 'b'
        """

        rootDir.file("build.gradle") << """
            plugins {
                id("$PROJECT_PLUGIN_1") version("1.0")
            }
        """

        rootDir.file("a/build.gradle") << """
            plugins {
                id("$PROJECT_PLUGIN_2") version("1.0")
            }
        """

        rootDir.file("b/build.gradle") << """
            plugins {
                id("$PROJECT_PLUGIN_1")
            }
        """
    }

    private void manifestHasPlugins() {
        // Settings plugin
        Map<String, Map> pluginDependencies = settingsPluginsAreSupported()
            ? [
                "my.settings.plugin:my.settings.plugin.gradle.plugin:1.0": [
                    relationship: "direct",
                    dependencies: ["com.example:settingPlugin:1.0"]
                ],
                "com.example:settingPlugin:1.0"                          : [
                    relationship: "indirect",
                    dependencies: ["org.test:foo:1.0"]
                ],
                "org.test:foo:1.0"                                       : [
                    relationship: "indirect",
                    dependencies: []
                ],

                // The project plugins are resolved at build level without any transitive deps
                "my.project.plugin1:my.project.plugin1.gradle.plugin:1.0": [
                    relationship: "direct",
                    dependencies: []
                ],
                "my.project.plugin2:my.project.plugin2.gradle.plugin:1.0": [
                    relationship: "direct",
                    dependencies: []
                ]
            ]
            : [
                // The project plugins are resolved at build level without any transitive deps
                "my.project.plugin1:my.project.plugin1.gradle.plugin:1.0": [
                    relationship: "direct",
                    dependencies: []
                ],
                "my.project.plugin2:my.project.plugin2.gradle.plugin:1.0": [
                    relationship: "direct",
                    dependencies: []
                ]
            ]

        // Plugin 1
        pluginDependencies.putAll([
            "my.project.plugin1:my.project.plugin1.gradle.plugin:1.0": [
                relationship: "direct",
                dependencies: ["com.example:plugin1:1.0"]
            ],
            "com.example:plugin1:1.0"                                : [
                relationship: "indirect",
                dependencies: []
            ]
        ])

        // Plugin 2
        pluginDependencies.putAll([
            "my.project.plugin2:my.project.plugin2.gradle.plugin:1.0": [
                relationship: "direct",
                dependencies: ["com.example:plugin2:1.0"]
            ],
            "com.example:plugin2:1.0"                                : [
                relationship: "indirect",
                dependencies: ["org.test:bar:1.0"]
            ],
            "org.test:bar:1.0"                                       : [
                relationship: "indirect",
                dependencies: []
            ]
        ])

        def manifest = gitHubManifest()
        manifest.sourceFile == "settings.gradle"
        manifest.assertResolved(pluginDependencies)
    }
}
