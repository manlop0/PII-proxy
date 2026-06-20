FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY backend/build.gradle.kts backend/settings.gradle.kts backend/gradlew ./
COPY backend/gradle/ gradle/

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY backend/src/ src/

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew shadowJar --no-daemon --parallel -x test -x javadoc

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/build/libs/*-fat.jar app.jar
COPY config.yaml config.yaml
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
