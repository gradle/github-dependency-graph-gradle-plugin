plugins {
    id("com.gradle.develocity").version("4.6.1")
    id("com.gradle.common-custom-user-data-gradle-plugin").version("2.8.0")
}

val isCI = !System.getenv("CI").isNullOrEmpty()

develocity {
    server = "https://ge.gradle.org"
    buildScan {
        uploadInBackground = !isCI

        obfuscation {
            ipAddresses { addresses -> addresses.map { _ -> "0.0.0.0" } }
        }

        System.getProperty("testGradleVersion")?.let { testGradleVersion ->
            tag("Test Gradle $testGradleVersion")
            value("testGradleVersion", testGradleVersion)
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "github-dependency-graph-gradle-plugin"
include("plugin")
include("plugin-test")
