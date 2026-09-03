# Build the distribution with Gradle, then run it on a JRE-only image.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY contract contract
COPY server server
RUN ./gradlew --no-daemon :server:installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/server/build/install/server ./
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080
# The container orchestrator probes /health/ready, which checks the database too.
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s \
    CMD wget -qO- http://localhost:8080/health/live || exit 1
ENTRYPOINT ["./bin/server"]
