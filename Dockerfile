# syntax=docker/dockerfile:1
#
# Self-contained build: the SPA with Node, the boot jar (with the SPA sealed in) with the JDK, and a slim JRE
# image that runs it.
#
#   docker build -t igaming-affiliate-program .
#   docker run --rm igaming-affiliate-program schema    # Flyway migrations, then exits
#   docker run --rm -p 8080:8080 igaming-affiliate-program

FROM node:22-alpine AS spa
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# Vite writes the build to ../src/main/resources/static, where Gradle seals it into the jar.
RUN npm run build

FROM eclipse-temurin:21-jdk AS jar
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
# Dependencies in a layer of their own, so a source change does not download the world again.
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src/ src/
COPY --from=spa /src/src/main/resources/static/ src/main/resources/static/
RUN ./gradlew --no-daemon --quiet bootJar

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app

WORKDIR /app

COPY --from=jar /src/build/libs/affiliate-app.jar /app/affiliate-app.jar

USER app

# 8080: the SPA and /api, the public port. 9464: /metrics and /health, never to be published.
EXPOSE 8080 9464

# JAVA_TOOL_OPTIONS, not JAVA_OPTS: the entrypoint is a bare `java -jar`, so JAVA_OPTS would be read by nobody.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"

# Exec form, so `schema` given as the command replaces CMD and reaches the launcher.
ENTRYPOINT ["java", "-jar", "/app/affiliate-app.jar"]
CMD ["app"]
