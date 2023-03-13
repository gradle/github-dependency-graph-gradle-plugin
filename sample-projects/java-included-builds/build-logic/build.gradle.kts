plugins {
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.6")
}

gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "org.test.build-logic-plugin"
            implementationClass = "BuildLogicPlugin"
        }
    }
}