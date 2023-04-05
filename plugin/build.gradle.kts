import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.jar.JarFile

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `java-test-fixtures`
    groovy
    id("com.github.johnrengelman.shadow")
}

val shadowImplementation: Configuration by configurations.creating
configurations["compileOnly"].extendsFrom(shadowImplementation)
configurations["testImplementation"].extendsFrom(shadowImplementation)

dependencies {
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly(kotlin("reflect"))
    shadowImplementation(platform(libs.jackson.platform))
    shadowImplementation(libs.jackson.databind)
    shadowImplementation(libs.jackson.kotlin) {
        version {
            strictly("2.12.3")
        }
        exclude(group = "org.jetbrains.kotlin")
        because("kotlin std lib is bundled with Gradle. 2.12.3 because higher versions depend upon Kotlin 1.5")
    }
    shadowImplementation("com.github.package-url:packageurl-java:1.4.1")
    shadowImplementation(libs.apache.httpclient)
    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation(libs.spock.core)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>() {
    kotlinOptions {
        apiVersion = "1.3"
        languageVersion = "1.3"
        jvmTarget = "1.8"
    }
}

tasks.test {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes["Implementation-Version"] = archiveVersion.get()
        attributes["Implementation-Title"] = "Gradle GitHub Dependency Extractor"
        attributes["Implementation-Vendor"] = "Gradle GitHub Dependency Extractor"
    }
}

tasks.withType<PluginUnderTestMetadata>().configureEach {
    pluginClasspath.from(shadowImplementation)
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    configurations = listOf(shadowImplementation)
    val projectGroup = project.group
    doFirst {
        configurations.forEach { configuration ->
            configuration.files.forEach { jar ->
                JarFile(jar).use { jf ->
                    jf.entries().iterator().forEach { entry ->
                        if (entry.name.endsWith(".class") && entry.name != "module-info.class") {
                            val packageName =
                                entry
                                    .name
                                    .substring(0..entry.name.lastIndexOf('/'))
                                    .replace('/', '.')
                            relocate(packageName, "${projectGroup}.shadow.$packageName")
                        }
                    }
                }
            }
        }
    }
}

configurations.archives.get().artifacts.clear()
configurations {
    artifacts {
        runtimeElements(shadowJarTask)
        apiElements(shadowJarTask)
        archives(tasks.shadowJar)
    }
}

// Add the shadow JAR to the runtime consumable configuration
configurations.apiElements.get().artifacts.clear()
configurations.apiElements.get().outgoing.artifact(tasks.shadowJar)
configurations.runtimeElements.get().outgoing.artifacts.clear()
configurations.runtimeElements.get().outgoing.artifact(tasks.shadowJar)

val shadowJarConfig = configurations.create("shadowJar") {
    isCanBeConsumed = true
    isCanBeResolved = false
}
repositories {
    mavenCentral()
}

artifacts {
    add(shadowJarConfig.name, tasks.shadowJar)
}

// Disabling default jar task as it is overridden by shadowJar
tasks.named("jar").configure {
    enabled = false
}
