#!/usr/bin/env bash
# Chapter 54 measurements on a real Kubernetes cluster with menu-service. About 20 minutes. Needs docker, a JDK 25
# (JAVA_HOME) and a cluster: ../../ch52-health-checks-actuator/verify/k3s-lab.sh starts one, or set KUBECTL (and
# LOAD_IMAGE, e.g. "kind load docker-image") for your own. The disruption experiment drains the cluster's first node.
#   ./run.sh                                  everything, then the API checks
#   ./run.sh cpu config scaling disruption
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C54_SOURCES:-build/src}
KUBECTL=${KUBECTL:-../../ch52-health-checks-actuator/verify/build/k3s/k3s kubectl}
LOAD_IMAGE=${LOAD_IMAGE:-../../ch52-health-checks-actuator/verify/build/k3s/k3s ctr images import -}
./build-libs.sh
DEPS=../../ch53-containerization/verify/build/lib/deps
rm -rf build/main build/tools build/image && mkdir -p build/main build/tools build/image/app/lib
$JAVA_HOME/bin/javac --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
$JAVA_HOME/bin/javac --release 25 -nowarn -XDsuppressNotes -d build/tools -cp "build/main:$(find $DEPS -mindepth 1 -maxdepth 1 -type d | paste -sd:)" tools/*.java
for d in "$DEPS"/*; do "$JAVA_HOME/bin/jar" --create --file "build/image/app/lib/$(basename "$d").jar" -C "$d" .; done
cp -r build/main build/tools build/image/app/
cp ../src/main/resources/logback.xml build/image/app/tools/
printf 'FROM c54/jre:25\nWORKDIR /app\nCOPY app/lib/ /app/lib/\nCOPY app/main/ /app/main/\nCOPY app/tools/ /app/tools/\nENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-cp", "/app/main:/app/tools:/app/lib/*", "KubeLab"]\n' > build/image/Dockerfile
docker build -q -t foodfleet/menu-service:ch54 build/image > /dev/null
if [[ "$LOAD_IMAGE" == *" -" ]]; then docker save foodfleet/menu-service:ch54 | ${LOAD_IMAGE} > /dev/null 2>&1
else ${LOAD_IMAGE} foodfleet/menu-service:ch54 > /dev/null; fi

$JAVA_HOME/bin/java -version 2>&1 | head -1
$KUBECTL version 2>/dev/null | grep -i server || true
MODES=${*:-cpu config scaling disruption}
for m in $MODES; do
  echo "== $m"
  $JAVA_HOME/bin/java -cp build/main:build/tools -Dc54.kubectl="$KUBECTL" Measure "$m"
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
