# Stage 1: Build
FROM dhi.io/amazoncorretto:25-debian13-dev AS build

WORKDIR /app

COPY --chmod=755 gradlew ./
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle/ ./gradle/
COPY src/ ./src/

# Server module
COPY server/build.gradle.kts ./server/
COPY server/src/ ./server/src/

ENV GRADLE_USER_HOME=/app/.gradle

RUN --mount=type=cache,target=/app/.gradle \
    ./gradlew --no-daemon :server:shadowJar

# Stage 2: Runtime
FROM dhi.io/amazoncorretto:25-debian13

WORKDIR /app

COPY --from=build --chown=65532:65532 --chmod=644 \
    /app/server/build/libs/app.jar /app/app.jar

USER 65532:65532

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
