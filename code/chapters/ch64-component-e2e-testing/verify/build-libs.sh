#!/usr/bin/env bash
# Builds what chapter 64 runs: the three services in one image (foodfleet/e2e:ch64, on chapter 54's jlink'd Java 25
# runtime; no base image is pulled) and chapter 62's PostgreSQL image, both loaded into the cluster; JUnit and the
# PostgreSQL driver (chapter 61's build-libs.sh); and sparse clones of Awaitility, Argo CD and Spring Boot for
# check_apis.sh. Needs docker, git, curl, a JDK 25 and a cluster (LOAD_IMAGE).
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C64_SOURCES:-build/src}
LOAD_IMAGE=${LOAD_IMAGE:-../../ch52-health-checks-actuator/verify/build/k3s/k3s ctr images import -}
mkdir -p "$SRC" build
../../ch62-unit-slice-integration-tests/verify/build-libs.sh > /dev/null       # chapter 61's libraries and c62/postgres:16
ln -sfn "$(cd ../../ch61-test-strategy/verify/build/lib && pwd)" build/lib
../../ch54-kubernetes-for-spring/verify/build-libs.sh > /dev/null              # the c54/jre:25 runtime image
rm -rf build/main build/image && mkdir -p build/main build/image/app/lib
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
cp -r build/main build/image/app/classes
cp build/lib/postgresql-*.jar build/image/app/lib/
printf 'FROM c54/jre:25\nCOPY app/ /app/\nENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-cp", "/app/classes:/app/lib/*", "dev.foodfleet.ch64.app.Main"]\n' > build/image/Dockerfile
docker build -q -t foodfleet/e2e:ch64 build/image > /dev/null
docker save foodfleet/e2e:ch64 c62/postgres:16 | $LOAD_IMAGE > /dev/null 2>&1

clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/awaitility/awaitility awaitility-4.3.0 "$SRC/awaitility" /awaitility/src/main/java/org/awaitility/
clone https://github.com/argoproj/argo-cd v3.5.5 "$SRC/argocd" /applicationset/generators/pull_request.go
clone https://github.com/spring-projects/spring-boot v4.1.1 "$SRC/boot" /core/spring-boot-test/src/main/java/org/springframework/boot/test/context/SpringBootTest.java
