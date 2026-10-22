# syntax=docker/dockerfile:1
# order-service: built in the image, run on a jlink'd runtime, layered so that a code change re-uploads kilobytes.
# Reference build for a Spring Boot 4 service (Maven wrapper in the repo). Run from the service's directory:
#   docker build -t registry.foodfleet.internal/order-service:$(git rev-parse --short HEAD) .

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q package -DskipTests
# Spring Boot's layers: dependencies, spring-boot-loader, snapshot-dependencies, application
RUN java -Djarmode=tools -jar target/order-service.jar extract --layers --launcher --destination /layers
# a runtime with only the modules the application uses
RUN jdeps --ignore-missing-deps --print-module-deps --multi-release 25 --recursive \
        --class-path '/layers/dependencies/BOOT-INF/lib/*' target/order-service.jar > /modules.txt \
 && jlink --add-modules "$(cat /modules.txt),jdk.crypto.ec,jdk.jcmd" --strip-debug --no-header-files --no-man-pages \
        --compress=zip-6 --output /jre

# glibc and CA certificates, with package metadata a scanner can read; no shell, no package manager; user 65532
FROM gcr.io/distroless/base-debian12:nonroot
COPY --from=build /jre /opt/java
WORKDIR /app
# least to most often changed: each layer is reused until something in it changes
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
# JDK 25 AOT cache from a training run that starts the context and exits (JEP 483/514/515)
RUN ["/opt/java/bin/java", "-XX:AOTCacheOutput=/app/app.aot", "-Dspring.context.exit=onRefresh", "-jar", "/app/order-service.jar"]
EXPOSE 8080 8081
# exec form: java is PID 1 and receives SIGTERM
ENTRYPOINT ["/opt/java/bin/java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-XX:AOTCache=/app/app.aot", \
            "-jar", "/app/order-service.jar"]
