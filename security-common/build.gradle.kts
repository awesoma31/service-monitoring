plugins {
    `java-library`
    `java-test-fixtures`
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.16") }
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    api("org.springframework:spring-webflux")
    api("org.springframework.cloud:spring-cloud-starter-openfeign")
    api("org.springdoc:springdoc-openapi-starter-common:2.9.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testFixturesImplementation("org.springframework.boot:spring-boot-test")
}

extra["coverageExclusions"] = listOf("**/*Configuration.class", "**/JwtProperties.class")
