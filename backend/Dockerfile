# syntax=docker/dockerfile:1

# The jar is platform independent, so Maven runs natively on the build machine even when the image is built for
# another platform (arm64 for the demo instance from an x86 PC); only the runtime stage is the target platform
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml lombok.config ./
# Strip CRLF in case the repo was checked out on Windows
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/music-app-*.jar app.jar

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
