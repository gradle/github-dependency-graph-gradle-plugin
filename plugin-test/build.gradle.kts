plugins {
    `java-test-fixtures`
    groovy
}

val extractorPlugin: Configuration by configurations.creating

repositories {
    mavenCentral()
}

dependencies {
    extractorPlugin(project(":plugin", "shadowJar"))
    testImplementation(gradleTestKit())
    testImplementation(libs.groovy.json)
    testImplementation(platform(libs.jackson.platform))
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.apache.commons.io)

    testFixturesApi(gradleTestKit())
    testFixturesApi(libs.spock.core)
    testFixturesApi(libs.spock.junit4)
    testFixturesApi(libs.junit.junit4)
    testFixturesApi(libs.jetbrains.annotations)

    testFixturesImplementation(gradleApi())
    testFixturesImplementation(libs.google.gson)
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
