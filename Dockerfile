FROM eclipse-temurin:21-jdk AS build
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

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
