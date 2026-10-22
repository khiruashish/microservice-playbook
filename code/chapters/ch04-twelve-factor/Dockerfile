# V. Build, release, run. This file only BUILDS an immutable image.
# The RELEASE is image digest + environment config; RUN is the platform starting it.
# Based on the Spring Boot reference "Dockerfiles" recipe (layered extraction + Java 25 AOT cache).
#
#   mvn -pl chapters/ch04-twelve-factor -am package -DskipTests
#   docker build -t foodfleet/zone-service:1.0.0 chapters/ch04-twelve-factor

FROM bellsoft/liberica-openjre-debian:25-cds AS builder
WORKDIR /builder
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM bellsoft/liberica-openjre-debian:25-cds
WORKDIR /application
# Dependencies change rarely, application code often: separate layers keep pushes and pulls small.
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# AOT cache training run (Java 25+): start the context once at build time, then exit.
# No real backing services exist at build time, so the run gets throwaway config and skips Flyway.
# The database URL is never contacted: the connection pool only connects on first use.
RUN java -XX:AOTCacheOutput=app.aot \
        -Dspring.context.exit=onRefresh \
        -Dspring.flyway.enabled=false \
        -Dspring.datasource.url=jdbc:postgresql://localhost:5432/training \
        -Dfoodfleet.zones.city=training \
        -Dfoodfleet.zones.default-delivery-fee-paise=0 \
        -Dfoodfleet.zones.max-delivery-km=1 \
        -jar application.jar

# Run as an unprivileged user; config arrives from the environment at RUN time.
USER 10001
EXPOSE 8085
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
