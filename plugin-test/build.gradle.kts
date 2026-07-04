plugins {
    `java-test-fixtures`
    groovy
}

val extractorPlugin: Configuration by configurations.creating

dependencies {
    extractorPlugin(project(":plugin", "shadowJar"))
    testImplementation(gradleTestKit())
    testImplementation(libs.groovy.json)
    testImplementation(platform(libs.jackson.platform))
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.apache.commons.io)

    testRuntimeOnly(project(":plugin", "shadowJar"))

    testFixturesApi(gradleTestKit())
    testFixturesApi(libs.spock.core)
    testFixturesApi(libs.spock.junit4)
    testFixturesApi(libs.junit.junit4)
    testFixturesApi(libs.jetbrains.annotations)

    testFixturesImplementation(gradleApi())
    testFixturesImplementation(libs.google.gson)
    testFixturesImplementation(libs.apache.commons.io)
}

val writeTestConfig by tasks.registering(WriteConfigTask::class) {
    dependsOn(extractorPlugin)
    generatedResourceDirectory.convention(project.layout.buildDirectory.dir("generated-test-resources"))
    properties.putAll(
        mapOf("extractorPlugin.jar.path" to extractorPlugin.singleFile.absolutePath)
    )
}

sourceSets.test {
    resources.srcDir(writeTestConfig.map { it.generatedResourceDirectory })
}

// Compile and run this module (and thus the Gradle version under test) on a specific JDK with
// -PtestJavaVersion=8, decoupled from the JDK used to build the :plugin fatjar. This lets the
// plugin be built with a fixed, modern JDK (as required by newer build tooling such as the
// shadow plugin) while still exercising older Gradle versions on the older JDKs they require.
// Test classes must be *compiled* for the test JDK too, not just executed on it, so we set a
// project-wide toolchain rather than only the test task's launcher. When unset, the module uses
// the JDK that is building the project.
val testJavaVersion = providers.gradleProperty("testJavaVersion")
    .map { JavaLanguageVersion.of(it.toInt()) }

if (testJavaVersion.isPresent) {
    java {
        toolchain {
            languageVersion = testJavaVersion.get()
        }
    }
}

tasks.withType<Test>().configureEach {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()

    // Test with a Gradle version different from the current with -DtestGradleVersion="7.6.1"
    System.getProperty("testGradleVersion")?.let { testGradleVersion ->
        systemProperties["testGradleVersion"] = testGradleVersion
    }

    // Re-run the tests when something from sample-projects changes
    inputs.dir("../sample-projects")
}

abstract class WriteConfigTask : DefaultTask() {
    @get:Input
    abstract val properties: MapProperty<String, String>

    @get:OutputDirectory
    abstract val generatedResourceDirectory: DirectoryProperty

    @TaskAction
    fun writeConfig() {
        val configFile = generatedResourceDirectory.file("test-config.properties").get().asFile
        configFile.writeText(properties.get().map { "${it.key}=${it.value.replace('\\', '/')}" }.joinToString("\n"))
    }
}
