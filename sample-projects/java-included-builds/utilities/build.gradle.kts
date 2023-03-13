plugins {
    `java-library`
    id("org.test.build-logic-plugin")
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.java-app:list:1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
