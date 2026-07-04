import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.gradle.publish.PublishTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Upgrade transitive dependencies in plugin classpath
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        constraints {
            // The com.gradleup.shadow plugin depends on vulnerable library releases.
            // We constrain these to newer, patched versions.
            classpath(libs.apache.commons.io)
            classpath(libs.apache.log4j.core)
        }
    }
}

plugins {
    kotlin("jvm") version(libs.versions.kotlin)
    alias(libs.plugins.plugin.publish)
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
    shadowImplementation(libs.github.packageurl)

    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spock.core)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_1_3
        languageVersion = KotlinVersion.KOTLIN_1_3
        jvmTarget = JvmTarget.JVM_1_8
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
    archiveClassifier = ""
    // Strip multi-release class files (Jackson ships JDK 11/17/21 optimized variants under
    // META-INF/versions). Older Gradle versions bundle an ASM that cannot read these newer
    // class file versions when they instrument the plugin classpath, failing with
    // "Unsupported class file major version". The Java 8 base classes work on all supported
    // Gradle versions. See https://github.com/gradle/gradle/issues/24390
    exclude("META-INF/versions/**")
    configurations = listOf(shadowImplementation)
    // Relocate every bundled package under `<group>.shadow.` so the plugin's own dependencies can
    // never clash with those of the build it is applied to. shadow 9.x does this natively via
    // auto-relocation, replacing the manual per-package relocation loop we maintained previously.
    enableAutoRelocation = true
    relocationPrefix = "${project.group}.shadow"
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
    website = "https://github.com/gradle/github-dependency-graph-gradle-plugin"
    vcsUrl = "https://github.com/gradle/github-dependency-graph-gradle-plugin"

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
    failOnWarning = true
    enableStricterValidation = true
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
    tagName = releaseTag
}

tasks.register<CreateGitHubRelease>("githubRelease") {
    dependsOn(createReleaseTag)
    tagName = releaseTag
    releaseName = releaseVersion
    notesFile = rootProject.layout.projectDirectory.file("release/changes.md")
}

tasks.withType(PublishTask::class).configureEach {
    notCompatibleWithConfigurationCache("$name task does not support configuration caching")
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

abstract class CreateGitHubRelease : DefaultTask() {

    @get:Input abstract val tagName: Property<String>

    @get:Input abstract val releaseName: Property<String>

    @get:InputFile abstract val notesFile: RegularFileProperty

    @get:Inject abstract val execOperations: ExecOperations

    @TaskAction
    fun action() {
        val tag = tagName.get()
        logger.info("Creating GitHub release ${releaseName.get()} for tag $tag")
        execOperations.exec {
            commandLine(
                "gh", "release", "create", tag,
                "--title", releaseName.get(),
                "--notes-file", notesFile.get().asFile.absolutePath
            )
            // The github-release workflow provides the token via this environment variable.
            environment("GH_TOKEN", System.getenv("GITHUB_DEPENDENCY_GRAPH_GIT_TOKEN") ?: "")
        }
    }
}

