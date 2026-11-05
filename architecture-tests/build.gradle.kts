plugins { java }

dependencies {
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

val inspectedModules = listOf(
    "user-service", "monitor-service", "check-service", "notification-service", "report-service",
    "integration-contracts")

tasks.test {
    dependsOn(inspectedModules.map { ":$it:classes" })
    // Tests import these class directories directly, not through the test runtime classpath.
    // Register them as inputs so Gradle reruns ArchUnit whenever inspected bytecode changes.
    inputs.files(inspectedModules.map { rootProject.layout.projectDirectory.dir("$it/build/classes/java/main") })
        .withPropertyName("inspectedServiceClasses")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("architecture.root", rootProject.projectDir.absolutePath)
}
