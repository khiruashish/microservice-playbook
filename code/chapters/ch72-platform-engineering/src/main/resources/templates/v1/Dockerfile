FROM c54/jre:25
COPY build/classes/ /app/classes/
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-cp", "/app/classes", "dev.foodfleet.{{pkg}}.Application"]
