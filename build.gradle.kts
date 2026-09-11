plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.awesoma"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val mapstructVersion = "1.6.3"
val lombokMapstructBindingVersion = "0.2.0"
val springdocVersion = "2.9.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.liquibase:liquibase-core")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    implementation("org.mapstruct:mapstruct:$mapstructVersion")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:$lombokMapstructBindingVersion")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

jacoco {
    toolVersion = "0.8.15"
}

// Excluded from coverage: no meaningful logic to test, only boilerplate
// (Lombok-generated members are skipped via lombok.addLombokGeneratedAnnotation).
val coverageExclusions = listOf(
    "**/MonitoringApplication.class",
    "**/config/**",
    "**/web/dto/**",
    "**/web/mapper/*Impl.class",
)

fun filteredClasses(classDirs: FileCollection): FileCollection =
    files(classDirs.files.map { fileTree(it) { exclude(coverageExclusions) } })

tasks.test {
    useJUnitPlatform()
    // Testcontainers' resource reaper mounts the Docker socket by its path inside the
    // daemon's host. Docker Desktop already uses this path; colima keeps the socket in
    // $HOME, and without this the reaper fails to start and every test errors out.
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(filteredClasses(classDirectories))
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(filteredClasses(classDirectories))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

// Coverage below the threshold must fail the build, not just report.
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
