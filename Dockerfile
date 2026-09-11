FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Dependency layer: re-resolved only when the build scripts change.
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts lombok.config ./
RUN ./gradlew --no-daemon dependencies --configuration runtimeClasspath

COPY src src
# Tests run via `./gradlew check`; they need a Docker daemon (Testcontainers),
# which is not available inside the image build.
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Running as root inside the container is an unnecessary privilege.
RUN addgroup -S app && adduser -S -G app app
USER app

COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
# Default heap sizing ignores the container limit, so make the JVM honour it.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
