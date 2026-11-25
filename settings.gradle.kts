plugins {
    id("com.gradle.develocity").version("4.6.1")
    id("io.github.gradle.develocity-conventions-plugin").version("0.16.0")
}

develocity {
    buildScan {
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
