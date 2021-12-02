plugins {
    `java-test-fixtures`
    `groovy`
}

dependencies {
    testImplementation(project(":plugin"))
    testImplementation(platform("org.gradle:distributions-dependencies"))
    testImplementation("org.gradle:internal-integ-testing")
}
