plugins {
    application
    id("org.test.build-logic-plugin")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-text:1.9")
    implementation(project(":utilities"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
}

application {
    mainClass = "org.example.java.app.app.App"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
