FROM eclipse-temurin:21-jdk-alpine AS build
# The Gradle module to package, e.g. monitor-service; set by docker-compose.
ARG MODULE
WORKDIR /app

# Dependency layer: re-resolved only when the build scripts change. Gradle needs the build
# script of every module listed in settings.gradle.kts, not only the one being packaged.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts lombok.config ./
COPY check-service/build.gradle.kts check-service/
COPY config-server/build.gradle.kts config-server/
COPY eureka-server/build.gradle.kts eureka-server/
COPY gateway/build.gradle.kts gateway/
COPY monitor-service/build.gradle.kts monitor-service/
COPY notification-service/build.gradle.kts notification-service/
RUN ./gradlew --no-daemon :${MODULE}:dependencies --configuration runtimeClasspath

COPY ${MODULE}/src ${MODULE}/src
# Tests run via `./gradlew check`; they need a Docker daemon (Testcontainers),
# which is not available inside the image build.
RUN ./gradlew --no-daemon :${MODULE}:bootJar -x test

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
