plugins {
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

// Settings every service shares: toolchain, repositories and the coverage gate. Each module
// only declares what it depends on and, optionally, which classes to leave out of coverage.
subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    group = "org.awesoma"
    version = "0.0.1-SNAPSHOT"

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.15"
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
        // Testcontainers' resource reaper mounts the Docker socket by its path inside the
        // daemon's host. Docker Desktop already uses this path; colima keeps the socket in
        // $HOME, and without this the reaper fails to start and every test errors out.
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
        finalizedBy("jacocoTestReport")
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")
        reports {
            xml.required = true
            html.required = true
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn("jacocoTestReport")
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

    // A module lists classes without logic to test as extra["coverageExclusions"]; its
    // script runs after this block, so the list is applied once the module is evaluated.
    afterEvaluate {
        @Suppress("UNCHECKED_CAST")
        val exclusions = (extra.properties["coverageExclusions"] as? List<String>) ?: emptyList()
        fun filtered(classDirs: FileCollection): FileCollection =
            files(classDirs.files.map { fileTree(it) { exclude(exclusions) } })
        tasks.named<JacocoReport>("jacocoTestReport") {
            classDirectories.setFrom(filtered(classDirectories))
        }
        tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
            classDirectories.setFrom(filtered(classDirectories))
        }
    }

    // Coverage below the threshold must fail the build, not just report.
    tasks.named("check") {
        dependsOn("jacocoTestCoverageVerification")
    }
}
