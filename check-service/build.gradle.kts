plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val springdocVersion = "2.9.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:$springdocVersion")
    // Liquibase migrates over JDBC; the service itself talks to the database through R2DBC.
    implementation("org.liquibase:liquibase-core")
    implementation("org.springframework:spring-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.postgresql:r2dbc-postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

extra["coverageExclusions"] = listOf(
    "**/CheckServiceApplication.class",
    "**/config/**",
    "**/web/dto/**",
)

// Tests read the same files the config server serves, without starting it or Eureka.
val configRepo = rootProject.file("config-server/src/main/resources/config-repo")
tasks.named<Test>("test") {
    systemProperty(
        "spring.config.import",
        "optional:file:$configRepo/application.yml,optional:file:$configRepo/check-service.yml")
    systemProperty("spring.cloud.config.enabled", "false")
    systemProperty("eureka.client.enabled", "false")
}
