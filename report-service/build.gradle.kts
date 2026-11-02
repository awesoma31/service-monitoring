plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val minioVersion = "8.6.0"
val archunitVersion = "1.4.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.liquibase:liquibase-core")
    implementation("io.minio:minio:$minioVersion")
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:minio")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

extra["coverageExclusions"] = listOf(
    "**/ReportServiceApplication.class",
    "**/config/**",
)

// Tests read the same files the config server serves, without starting it or Eureka.
val configRepo = rootProject.file("config-server/src/main/resources/config-repo")
tasks.named<Test>("test") {
    systemProperty(
        "spring.config.import",
        "optional:file:$configRepo/application.yml,optional:file:$configRepo/report-service.yml")
    systemProperty("spring.cloud.config.enabled", "false")
    systemProperty("eureka.client.enabled", "false")
}
