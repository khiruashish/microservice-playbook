# AppCDS: a training run at build time records the application's classes into a dynamic archive, mapped at startup.
FROM c60/jre:25
COPY --chown=10001:10001 app/ /app/
RUN ["java", "-XX:ArchiveClassesAtExit=/app/app.jsa", "-cp", "/app/*", "Trainer"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:SharedArchiveFile=/app/app.jsa", "-cp", "/app/*", "StartupLab"]
