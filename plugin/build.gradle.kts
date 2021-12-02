import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `java-test-fixtures`
    `groovy`
    id("com.github.johnrengelman.shadow")
}

val jettyVersion = "9.4.36.v20210114"

dependencies {
    compileOnly(kotlin("stdlib-jdk8"))
    implementation(platform(libs.jackson.platform))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation("com.github.package-url:packageurl-java:1.4.1")
    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("org.spockframework:spock-core:2.0-groovy-3.0")

    testFixturesApi("org.eclipse.jetty:jetty-http:$jettyVersion")
    testFixturesApi("org.eclipse.jetty:jetty-webapp:$jettyVersion")
    testFixturesImplementation("com.google.guava:guava:30.1.1-jre")
    testFixturesImplementation("com.google.code.gson:gson:2.8.6")
    testFixturesImplementation("junit:junit:4.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>() {
    kotlinOptions {
        apiVersion = "1.3"
        jvmTarget = "1.8"
    }
}

gradlePlugin {
    // Define the plugin
    val greeting by plugins.creating {
        id = "org.gradle.github.dependency.extractor.greeting"
        implementationClass = "org.gradle.github.dependency.extractor.GithubDependencyExtractorPlugin"
    }
}

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {
}

gradlePlugin.testSourceSets(functionalTestSourceSet)
configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])

// Add a task to run the functional tests
val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    dependsOn(tasks.named("shadowJar"))
}

tasks.check {
    // Run the functional tests as part of `check`
    dependsOn(functionalTest)
}

tasks.test {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
