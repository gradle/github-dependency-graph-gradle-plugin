package org.gradle.test.fixtures

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.util.GradleVersion

class PluginPublisher {
    private final MavenRepository mavenRepo
    private final TestFile rootDir

    PluginPublisher(MavenRepository mavenRepo, TestFile rootDir) {
        this.mavenRepo = mavenRepo
        this.rootDir = rootDir
    }

    void publishSettingsPlugin(String pluginModule, String pluginId, String pluginDependencies = "") {
        publishPlugin(pluginModule, pluginId, "org.gradle.api.initialization.Settings", pluginDependencies)
    }

    void publishProjectPlugin(String pluginModule, String pluginId, String pluginDependencies = "") {
        publishPlugin(pluginModule, pluginId, "org.gradle.api.Project", pluginDependencies)
    }

    private void publishPlugin(String pluginModule, String pluginId, String pluginTarget, String pluginDependencies) {
        rootDir.file( pluginModule, "src/main/java/Plugin_${pluginModule}.java") << """
            import org.gradle.api.Plugin;

            public class Plugin_${pluginModule} implements Plugin<$pluginTarget> {
                @Override
                public void apply($pluginTarget target) {
                    System.out.println("${pluginId} from repository applied");
                }
            }
        """
        rootDir.file(pluginModule, "settings.gradle") << "rootProject.name='$pluginModule'"
        rootDir.file(pluginModule, "build.gradle") << """
            plugins {
                id("java-gradle-plugin")
                id("maven-publish")
            }
            group = "com.example"
            version = "1.0"
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
            dependencies {
                $pluginDependencies
            }
            publishing {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            }
            gradlePlugin {
                plugins {
                    publishedPlugin {
                        id = '${pluginId}'
                        implementationClass = 'Plugin_${pluginModule}'
                    }
                }
            }
        """


        def pluginDir = rootDir.file(pluginModule)

        def executer = new SimpleGradleExecuter(pluginDir, rootDir.file("test-kit"), GradleVersion.current().version)
        executer.withTasks("publish").run()
        pluginDir.forceDeleteDir()

        mavenRepo.module("com.example", pluginModule, "1.0").pomFile.assertExists()
    }

}

