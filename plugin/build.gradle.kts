import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.jar.JarFile

plugins {
    kotlin("jvm") version(libs.versions.kotlin)
    id("com.gradle.plugin-publish") version "1.2.1"
    id("com.github.breadmoirai.github-release") version "2.4.1"
    signing
    groovy
    alias(libs.plugins.shadow.jar)
}

val releaseVersion = loadReleaseVersion()
val releaseNotes = loadReleaseNotes()
val releaseTag = "v${releaseVersion}"
val pluginGroup = "org.gradle"
val pluginArtifactId = "github-dependency-graph-gradle-plugin"

group = pluginGroup
version = releaseVersion

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
    shadowImplementation(libs.github.packageurl)
    shadowImplementation(libs.apache.httpclient)

    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spock.core)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_1_3)
        languageVersion.set(KotlinVersion.KOTLIN_1_3)
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks.test {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes["Implementation-Version"] = archiveVersion.get()
        attributes["Implementation-Title"] = "GitHub Dependency Graph Gradle Plugin"
        attributes["Implementation-Vendor"] = "Gradle Inc"
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

artifacts {
    add(shadowJarConfig.name, tasks.shadowJar)
}

// Disabling default jar task as it is overridden by shadowJar
tasks.named("jar").configure {
    enabled = false
}

/**
 * Configuration for publishing the plugin, locally and to the Gradle Plugin Portal.
 */
gradlePlugin {
    website.set("https://github.com/gradle/github-dependency-graph-gradle-plugin")
    vcsUrl.set("https://github.com/gradle/github-dependency-graph-gradle-plugin")

    plugins {
        create("dependencyGraphPlugin") {
            id = "${pluginGroup}.${pluginArtifactId}"
            implementationClass = "org.gradle.github.GitHubDependencyGraphPlugin"
            displayName = "Generates a GitHub Dependency Graph"
            description = releaseNotes
            tags.addAll("github", "dependencies", "dependency-graph", "dependency-submission")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "localPlugins"
            url = uri("../build/local-plugin-repository")
        }
    }
    publications {
        create<MavenPublication>("pluginMaven") {
            artifactId = pluginArtifactId
        }
    }
}

tasks.withType(ValidatePlugins::class).configureEach {
    failOnWarning.set(true)
    enableStricterValidation.set(true)
}

signing {
    setRequired({ gradle.taskGraph.hasTask(":plugin:publishPlugins") })

    useInMemoryPgpKeys(
        providers.environmentVariable("PGP_SIGNING_KEY").orNull,
        providers.environmentVariable("PGP_SIGNING_KEY_PASSPHRASE").orNull
    )
}
tasks.withType(Sign::class).configureEach {
    notCompatibleWithConfigurationCache("$name task does not support configuration caching")
}

fun loadReleaseVersion():String {
    val releaseVersionFile = rootProject.layout.projectDirectory.file("release/version.txt")
    return providers.fileContents(releaseVersionFile).asText.map { it.trim() }.get()
}

fun loadReleaseNotes():String {
    val releaseNotesFile = rootProject.layout.projectDirectory.file("release/changes.md")
    return providers.fileContents(releaseNotesFile).asText.map { it.trim() }.get()
}

val createReleaseTag = tasks.register<CreateGitTag>("createReleaseTag") {
    tagName.set(releaseTag)
}

githubRelease {
    setToken(System.getenv("GITHUB_DEPENDENCY_GRAPH_GIT_TOKEN") ?: "")
    owner.set("gradle")
    repo.set("github-dependency-graph-gradle-plugin")
    releaseName.set(releaseVersion)
    tagName.set(releaseTag)
    body.set(releaseNotes)
}

tasks.named("githubRelease").configure {
    dependsOn(createReleaseTag)
}

abstract class CreateGitTag : DefaultTask() {

    @get:Input abstract val tagName: Property<String>

    @get:Inject abstract val execOperations: ExecOperations

    @TaskAction
    fun action() {
        logger.info("Tagging HEAD as ${tagName.get()}")
        execOperations.exec {
            commandLine("git", "tag", "-f", tagName.get())
        }
        execOperations.exec {
            commandLine("git", "push", "origin", "-f", "--tags")
        }
    }
}

