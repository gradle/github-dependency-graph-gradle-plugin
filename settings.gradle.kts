pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "1.5.21"
        id("com.github.johnrengelman.shadow") version "7.1.0"
    }
}

rootProject.name = "github-dependency-extractor"
include("plugin")
