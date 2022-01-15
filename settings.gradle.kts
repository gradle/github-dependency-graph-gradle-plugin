import org.gradle.api.internal.FeaturePreviews

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "1.5.32"
        id("com.github.johnrengelman.shadow") version "7.1.0"
    }
}
plugins {
    id("com.gradle.enterprise").version("3.8.1")
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin").version("0.7.6")
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
    buildScan {
        val buildUrl = System.getenv("BUILD_URL") ?: ""
        if (buildUrl.isNotBlank()) {
            link("Build URL", buildUrl)
        }
    }
}

apply(from = "gradle/build-cache-configuration.settings.gradle.kts")

rootProject.name = "github-dependency-extractor"
include("plugin")
include("plugin-test")

FeaturePreviews.Feature.values().forEach { feature ->
    if (feature.isActive) {
        enableFeaturePreview(feature.name)
    }
}
