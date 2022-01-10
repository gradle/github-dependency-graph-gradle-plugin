plugins {
    `java-test-fixtures`
    `groovy`
}

val extractorPlugin: Configuration by configurations.creating

dependencies {
    extractorPlugin(project(":plugin", "shadowJar"))
    testImplementation(gradleTestKit())
    testImplementation("org.codehaus.groovy:groovy-json:3.0.9")

    testFixturesApi(gradleTestKit())
    testFixturesApi(libs.spock.core)
    testFixturesApi("junit:junit:4.13.2")
    testFixturesApi("org.jetbrains:annotations:22.0.0")

    testFixturesImplementation(gradleApi())
    testFixturesImplementation("com.google.code.gson:gson:2.8.9")
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

    // use -PtestVersions=all or -PtestVersions=1.2,1.3…
    val integTestVersionsSysProp = "org.gradle.integtest.versions"
    if (project.hasProperty("testVersions")) {
        systemProperties[integTestVersionsSysProp] = project.property("testVersions")
    } else {
        systemProperties[integTestVersionsSysProp] = "partial"
    }
}

abstract class WriteConfigTask : DefaultTask() {
    @get:Input
    abstract val properties: MapProperty<String, Any>

    @get:OutputDirectory
    abstract val generatedResourceDirectory: DirectoryProperty

    @TaskAction
    fun writeConfig() {
        val configFile = generatedResourceDirectory.file("test-config.properties").get().asFile
        configFile.writeText(properties.get().map { "${it.key}=${it.value}" }.joinToString("\n"))
    }
}
