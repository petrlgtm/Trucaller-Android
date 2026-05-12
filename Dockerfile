# Stage 1: Build the Ktor backend fat-JAR
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy only what the backend build needs (skip the Android :app module)
COPY build.gradle.kts .
COPY gradle/ gradle/
COPY gradlew .
COPY backend/ backend/

# Override root build.gradle.kts: strip Android/Google plugins that need google() repo + Android SDK.
# The :backend module declares all its own plugins directly; the root only needs JVM/serialization/shadow.
RUN printf 'plugins {\n\
    alias(libs.plugins.kotlin.jvm) apply false\n\
    alias(libs.plugins.kotlin.serialization) apply false\n\
    alias(libs.plugins.shadow) apply false\n\
}\n' > build.gradle.kts

# Create a minimal settings.gradle.kts that includes only :backend
# (the root settings.gradle.kts also includes :app which needs Android SDK)
RUN printf 'pluginManagement {\n\
    repositories {\n\
        mavenCentral()\n\
        gradlePluginPortal()\n\
    }\n\
}\n\
plugins {\n\
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"\n\
}\n\
dependencyResolutionManagement {\n\
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n\
    repositories { mavenCentral() }\n\
}\n\
rootProject.name = "TruCaller"\n\
include(":backend")\n' > settings.gradle.kts

RUN gradle :backend:shadowJar --no-daemon

# Stage 2: Minimal JRE runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/backend/build/libs/trucaller-backend.jar app.jar

ENV MONGO_URI=""
ENV JWT_SECRET=""
ENV PORT=8080

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
