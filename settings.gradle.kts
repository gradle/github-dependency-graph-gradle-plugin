plugins {
    id("com.gradle.enterprise").version("3.12.3")
    id("com.gradle.common-custom-user-data-gradle-plugin").version("1.8.2")
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "Gradle public repository"
            url = uri("https://repo.gradle.org/gradle/public")
            content {
                includeGroup("net.rubygrapefruit")
                includeModule("classycle", "classycle")
                includeModule("flot", "flot")
                includeModule("org.gradle", "gradle-tooling-api")
                includeModuleByRegex("org.gradle", "docs-asciidoctor-extensions(-base)?")
            }
        }
        google {
            content {
                includeGroup("com.android.databinding")
                includeGroupByRegex("com\\.android\\.tools(\\.[a-z.\\-]*)?")
            }
        }
        maven {
            name = "CHAMP libs"
            url = uri("https://releases.usethesource.io/maven/")
            mavenContent {
                includeGroup("io.usethesource")
            }
        }
        mavenCentral()
    }
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
