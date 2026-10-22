# Build the jar first (verify/run.sh does: javac, then jar), then:
#   docker build -t foodfleet/menu-service:0.1.0 .
# The image holds only a JRE and the jar, and runs as a non-root user.
ARG JRE_IMAGE=eclipse-temurin:25-jre
FROM ${JRE_IMAGE}
COPY build/menu-service.jar /app/menu-service.jar
USER 10001
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/menu-service.jar"]
