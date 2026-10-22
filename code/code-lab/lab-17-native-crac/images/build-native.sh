#!/usr/bin/env bash
# menu-service as a GraalVM native executable. Spring Boot's parent POM has a `native` profile that runs Spring AOT
# and the GraalVM Native Build Tools plugin. Needs GraalVM 25 (GRAALVM_HOME) and ~2 GB of memory: chapter 60's
# 33.6 MB executable took 205 s and 1.73 GB to build. Without GraalVM locally, build-image uses a buildpack instead.
set -euo pipefail
cd "${1:?path to a Spring Boot 4 project}"
if [ -n "${GRAALVM_HOME:-}" ]; then
  JAVA_HOME=$GRAALVM_HOME mvn -B -Pnative native:compile -DskipTests
  ls -lh target/ | grep -v '\.jar' | tail -3
else
  mvn -B -Pnative spring-boot:build-image -DskipTests      # needs Docker; pulls the Paketo builder
fi
# Reflection the analysis can't see (a class named in configuration, a JSON type built by reflection) needs hints:
# @RegisterReflectionForBinding on a configuration class, or a RuntimeHintsRegistrar. Run the tests natively to
# find what's missing: mvn -PnativeTest test
