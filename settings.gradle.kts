plugins {
    id("com.gradle.enterprise").version("3.13")
    id("com.gradle.common-custom-user-data-gradle-plugin").version("1.10")
}

val isCI = !System.getenv("CI").isNullOrEmpty()

gradleEnterprise {
    server = "https://ge.gradle.org"
    buildScan {
        publishAlways()
        capture { isTaskInputFiles = true }
        isUploadInBackground = !isCI

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
