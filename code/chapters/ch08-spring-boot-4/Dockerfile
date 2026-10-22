# eta-service image: layered jar + Java 25 AOT cache (JEP 483 / 514), as in chapter 4.
#
#   mvn -pl chapters/ch08-spring-boot-4 -am package -DskipTests
#   docker build -t foodfleet/eta-service:1.0.0 chapters/ch08-spring-boot-4

FROM bellsoft/liberica-openjre-debian:25-cds AS builder
WORKDIR /builder
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM bellsoft/liberica-openjre-debian:25-cds
WORKDIR /application
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# Training run: start the context once and exit, recording loaded and linked classes into app.aot.
# eta-service has no database; the downstream URLs are never called during startup.
RUN java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar application.jar

USER 10001
EXPOSE 8088
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
