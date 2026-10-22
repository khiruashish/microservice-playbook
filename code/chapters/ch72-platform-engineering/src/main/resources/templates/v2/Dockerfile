FROM c54/jre:25
COPY build/classes/ /app/classes/
USER 10001
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=70", "-XX:+ExitOnOutOfMemoryError", "-cp", "/app/classes", "dev.foodfleet.{{pkg}}.Application"]
