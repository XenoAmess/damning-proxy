# syntax=docker/dockerfile:1

# Build stage: compile the Quarkus application and the Vue admin UI.
FROM maven:3.9-eclipse-temurin-21 AS build

# Enable pnpm for frontend dependency management (matches CI).
RUN corepack enable pnpm

WORKDIR /workspace

# Copy Maven descriptor first to leverage dependency cache when possible.
COPY pom.xml ./

# Download Maven dependencies before copying the full source so that
# subsequent code changes do not invalidate the already-populated layer.
RUN mvn dependency:go-offline

# Copy frontend descriptors and install dependencies with pnpm.
COPY admin-web/package.json admin-web/pnpm-lock.yaml ./admin-web/
RUN cd admin-web && pnpm install --frozen-lockfile

# Copy the rest of the source.
COPY . .

# Build the admin UI into src/main/resources/META-INF/resources/admin/.
RUN cd admin-web && pnpm run build

# Package the runnable Quarkus fast-jar, skipping the frontend build in Maven
# because it was already produced by pnpm above.
RUN mvn clean package -DskipTests -Dskip.frontend.build=true

# Runtime stage: keep only the built application.
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the Quarkus fast-jar layout.
COPY --from=build /workspace/target/quarkus-app /app

# Persistent H2 data and other runtime state are stored under /data.
VOLUME /data

EXPOSE 12360

# Default JVM options point user.home to /data so the H2 file lives on the volume.
ENV JAVA_OPTS="-Duser.home=/data"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]
