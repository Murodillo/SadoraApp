# Build the distribution with Gradle, then run it on a JRE-only image.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY contract contract
COPY server server
RUN ./gradlew --no-daemon :server:installDist

# The OpenTelemetry Java agent, pinned by checksum. Every image carries it, but it only
# runs where the environment asks for it with SADORA_OTEL=1: staging does, production
# does not yet. Keep OTEL_AGENT_VERSION in step with opentelemetry in
# gradle/libs.versions.toml — the app's API must not be newer than the agent's.
ARG OTEL_AGENT_VERSION=2.32.0
ARG OTEL_AGENT_SHA256=f787eb6c7f3d18e69a431e108a15278d25ee37f83d68b678f621e063f3988f82
RUN curl -fsSLo /src/opentelemetry-javaagent.jar \
      "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" \
 && echo "${OTEL_AGENT_SHA256}  /src/opentelemetry-javaagent.jar" | sha256sum -c -

FROM eclipse-temurin:21-jre
# The commit this image was built from. CI passes it; /health reports it, and the deploy
# refuses to call itself done until the running API answers with the release it shipped.
ARG SADORA_RELEASE=local
ENV SADORA_RELEASE=${SADORA_RELEASE}
LABEL org.opencontainers.image.source="https://github.com/Murodillo/SadoraApp" \
      org.opencontainers.image.revision="${SADORA_RELEASE}"
WORKDIR /app
COPY --from=build /src/server/build/install/server ./
COPY --from=build /src/opentelemetry-javaagent.jar ./otel/opentelemetry-javaagent.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080
# The container orchestrator probes /health/ready, which checks the database too.
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s \
    CMD wget -qO- http://localhost:8080/health/live || exit 1
# SADORA_OTEL=1 loads the agent. Checked here rather than set as a -javaagent flag in
# compose, so a rollback to an image from before the agent still starts — without
# telemetry — instead of failing on a jar it does not have.
ENTRYPOINT ["sh", "-c", "if [ \"$SADORA_OTEL\" = 1 ] && [ -f otel/opentelemetry-javaagent.jar ]; then export JAVA_OPTS=\"$JAVA_OPTS -javaagent:/app/otel/opentelemetry-javaagent.jar\"; fi; exec ./bin/server"]
