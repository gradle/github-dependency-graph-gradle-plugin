package org.gradle.github.dependency.extractor


import org.gradle.test.fixtures.PluginPublisher
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

class PluginDependencyExtractorTest extends BaseExtractorTest {
    private static final SETTINGS_PLUGIN = "my.settings.plugin"
    private static final PROJECT_PLUGIN_1 = "my.project.plugin1"
    private static final PROJECT_PLUGIN_2 = "my.project.plugin2"

    private File settingsFile
    private File buildFile

    def setup() {
        applyExtractorPlugin()
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
        manifestNames == ["build :", "project :", "project :a"]
        // Project ':b' is not reported, since the plugin is loaded in parent project

        manifestHasSettingsPlugin("build :")
        manifestHasPlugin1("project :")
        manifestHasPlugin2("project :a")
    }

    def "extracts all plugin dependencies from multi project buildSrc build"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << "apply plugin: 'java'"

        createMultiProjectBuildWithPlugins(file("buildSrc"))

        when:
        run()

        then:
        manifestNames == ["build :", "project :", "project :buildSrc", "project :buildSrc:a"]
        manifestHasSettingsPlugin("build :")
        manifestIsEmpty("project :")
        manifestHasPlugin1("project :buildSrc")
        manifestHasPlugin2("project :buildSrc:a")
    }

    def "extracts all plugin dependencies from multi project included build"() {
        given:
        settingsFile << "includeBuild 'included-child'"
        buildFile << "apply plugin: 'java'"

        createMultiProjectBuildWithPlugins(file("included-child"))

        when:
        run()

        then:
        manifestNames == ["build :", "project :", "project :included-child", "project :included-child:a"]
        manifestHasSettingsPlugin("build :")
        manifestIsEmpty("project :")
        manifestHasPlugin1("project :included-child")
        manifestHasPlugin2("project :included-child:a")
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
        manifestNames == ["build :", "project :", "project :included-plugin", "project :included-plugin:a"]
        manifestHasSettingsPlugin("build :")
        manifestIsEmpty("project :")
        manifestHasPlugin1("project :included-plugin")
        manifestHasPlugin2("project :included-plugin:a")
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

    private boolean manifestHasSettingsPlugin(String manifestName) {
        if (settingsPluginsAreSupported()) {
            gitHubManifest(manifestName).assertResolved([
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
            ])
        } else {
            gitHubManifest(manifestName).assertResolved([
                // The project plugins are resolved at build level without any transitive deps
                "my.project.plugin1:my.project.plugin1.gradle.plugin:1.0": [
                    relationship: "direct",
                    dependencies: []
                ],
                "my.project.plugin2:my.project.plugin2.gradle.plugin:1.0": [
                    relationship: "direct",
                    dependencies: []
                ]
            ])

        }
    }

    private boolean manifestHasPlugin1(String manifestName) {
        gitHubManifest(manifestName).assertResolved([
            "my.project.plugin1:my.project.plugin1.gradle.plugin:1.0": [
                relationship: "direct",
                dependencies: ["com.example:plugin1:1.0"]
            ],
            "com.example:plugin1:1.0"                                : [
                relationship: "indirect",
                dependencies: []
            ]
        ])
    }

    private boolean manifestHasPlugin2(String manifestName) {
        gitHubManifest(manifestName).assertResolved([
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
    }

    private boolean manifestIsEmpty(String manifestName) {
        gitHubManifest(manifestName).assertResolved([:])
    }

}
