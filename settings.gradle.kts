pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "1.8.10"
        id("com.github.johnrengelman.shadow") version "8.1.0"
    }
}
plugins {
    id("com.gradle.enterprise").version("3.12.3")
    id("com.gradle.common-custom-user-data-gradle-plugin").version("1.8.2")
}

gradleEnterprise {
    server = "https://ge.gradle.org"
    buildScan {
        publishAlways()
        obfuscation {
            ipAddresses { addresses -> addresses.map { _ -> "0.0.0.0" } }
        }

        System.getProperty("testGradleVersion")?.let { testGradleVersion ->
            tag("Test Gradle $testGradleVersion")
            value("testGradleVersion", testGradleVersion)
        }
    }
}

apply(from = "gradle/build-cache-configuration.settings.gradle.kts")

rootProject.name = "github-dependency-extractor"
include("plugin")
include("plugin-test")
