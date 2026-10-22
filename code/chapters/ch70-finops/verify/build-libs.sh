#!/usr/bin/env bash
# Builds what chapter 70 runs: menu-service in an image (foodfleet/finops:ch70, on chapter 54's jlink'd Java 25 runtime)
# loaded into the cluster; JUnit (chapter 61's build-libs.sh); and sparse clones of the Vertical Pod Autoscaler and
# OpenCost for check_apis.sh. Needs docker, git, curl, a JDK 25 and a cluster (LOAD_IMAGE).
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C70_SOURCES:-build/src}
LOAD_IMAGE=${LOAD_IMAGE:-../../ch52-health-checks-actuator/verify/build/k3s/k3s ctr images import -}
mkdir -p "$SRC" build
C61_SOURCES="$(cd ../../ch61-test-strategy/verify && pwd)/build/src" ../../ch61-test-strategy/verify/build-libs.sh > /dev/null
ln -sfn "$(cd ../../ch61-test-strategy/verify/build/lib && pwd)" build/lib
../../ch54-kubernetes-for-spring/verify/build-libs.sh > /dev/null              # the c54/jre:25 runtime image
rm -rf build/main build/image && mkdir -p build/main build/image/app
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
cp -r build/main build/image/app/classes
printf 'FROM c54/jre:25\nCOPY app/ /app/\nENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=70", "-cp", "/app/classes", "dev.foodfleet.ch70.app.MenuApi"]\n' > build/image/Dockerfile
docker build -q -t foodfleet/finops:ch70 build/image > /dev/null
docker save foodfleet/finops:ch70 | $LOAD_IMAGE > /dev/null 2>&1

clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/kubernetes/autoscaler vertical-pod-autoscaler/v1.8.0 "$SRC/vpa" /vertical-pod-autoscaler/pkg/recommender/config/config.go \
  /vertical-pod-autoscaler/pkg/recommender/logic/recommender.go
clone https://github.com/opencost/opencost v2.5.3 "$SRC/opencost" /pkg/costmodel/allocation_helpers.go /configs/default.json
