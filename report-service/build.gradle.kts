plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val minioVersion = "8.6.0"
val springdocVersion = "2.9.1"
val pdfboxVersion = "3.0.8"
// Liberation Sans for PDFs with Cyrillic; only its font files (SIL Open Font License) are used.
val libreFontsVersion = "2.0.3"

dependencies {
    implementation(project(":security-common"))
    implementation(project(":integration-contracts"))
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
    runtimeOnly("com.github.librepdf:openpdf-fonts-extra:$libreFontsVersion") { isTransitive = false }
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    implementation("org.liquibase:liquibase-core")
    implementation("io.minio:minio:$minioVersion")
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.awaitility:awaitility")
    testImplementation(testFixtures(project(":security-common")))
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:minio")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

extra["coverageExclusions"] = listOf(
    "**/ReportServiceApplication.class",
    "**/config/**",
    "**/web/dto/**",
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
