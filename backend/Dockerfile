# =============================================================================================
# TuneFlow backend image - multi-stage build (docs section 32).
# Stage 1 builds with the Maven wrapper-less toolchain, stage 2 ships only the JRE + jar.
# =============================================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependency layer first: it only invalidates when the POM changes, which keeps rebuilds fast.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/tuneflow-backend-*.jar /workspace/app.jar

# ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# curl is used by the container healthcheck; FFmpeg powers the media pipeline (docs section 16).
RUN apk add --no-cache curl ffmpeg tzdata \
    && addgroup -S tuneflow && adduser -S tuneflow -G tuneflow \
    && mkdir -p /app/data/media /app/logs && chown -R tuneflow:tuneflow /app

COPY --from=build /workspace/app.jar /app/app.jar

USER tuneflow
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport" \
    SPRING_PROFILES_ACTIVE=prod \
    TUNEFLOW_MEDIA_PATH=/app/data/media

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
