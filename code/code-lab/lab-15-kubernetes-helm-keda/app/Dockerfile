# No base image is pulled: c54/jre:25 is chapter 54's jlink'd Java 25 runtime, built locally
# (code/chapters/ch54-kubernetes-for-spring/verify/build-libs.sh); it runs as user 10001. Any Java 25 runtime works.
FROM c54/jre:25
COPY --chown=10001:10001 menu-service.jar /app/
# The AOT cache the chart expects at /app/app.aot (ADR-060), trained through the HTTP path. The class path must be
# jars: the JVM refuses to write a cache for a class path with a non-empty directory on it.
RUN ["java", "-XX:AOTCacheOutput=/app/app.aot", "-cp", "/app/menu-service.jar", "MenuStandIn", "train"]
ENTRYPOINT ["java", "-cp", "/app/menu-service.jar", "MenuStandIn"]
