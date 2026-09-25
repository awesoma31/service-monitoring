plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Only the application entry point: nothing with logic to cover.
extra["coverageExclusions"] = listOf("**/*Application.class")

// Tests read the same files the config server serves, without starting it or Eureka.
val configRepo = rootProject.file("config-server/src/main/resources/config-repo")
tasks.named<Test>("test") {
    systemProperty(
        "spring.config.import",
        "optional:file:$configRepo/application.yml,optional:file:$configRepo/gateway.yml")
    systemProperty("spring.cloud.config.enabled", "false")
    systemProperty("eureka.client.enabled", "false")
}
