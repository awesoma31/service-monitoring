FROM eclipse-temurin:21-jdk-alpine AS build
# The Gradle module to package, e.g. monitor-service; set by docker-compose.
ARG MODULE
WORKDIR /app

# Gradle needs the build script of every module listed in settings.gradle.kts, not only the
# one being packaged.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts lombok.config ./
COPY check-service/build.gradle.kts check-service/
COPY config-server/build.gradle.kts config-server/
COPY eureka-server/build.gradle.kts eureka-server/
COPY gateway/build.gradle.kts gateway/
COPY monitor-service/build.gradle.kts monitor-service/
COPY notification-service/build.gradle.kts notification-service/
COPY ${MODULE}/src ${MODULE}/src

# The Gradle distribution and every dependency live in a BuildKit cache shared by all the
# images and kept between builds, so they are downloaded once rather than per image and per
# change of a build script. The images build in parallel, and two Gradle runs filling the
# same fresh cache at once break each other, so the cache is taken by one build at a time.
# Tests run via `./gradlew check`; they need a Docker daemon (Testcontainers), which is not
# available inside the image build.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon :${MODULE}:bootJar -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
ARG MODULE
WORKDIR /app

# Running as root inside the container is an unnecessary privilege.
RUN addgroup -S app && adduser -S -G app app
USER app

COPY --from=build /app/${MODULE}/build/libs/*.jar app.jar
EXPOSE 8080
# Default heap sizing ignores the container limit, so make the JVM honour it.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
