#!/usr/bin/env bash
# Chapter 57 measurements: Envoy sidecars in front of menu-service and its caller on a real cluster. About 15 minutes.
# Needs docker, git, curl, openssl, a JDK 25 (JAVA_HOME) and a cluster with metrics-server:
# K3S_DISABLE=traefik ../../ch52-health-checks-actuator/verify/k3s-lab.sh starts one, or set KUBECTL (and LOAD_IMAGE,
# e.g. "kind load docker-image") for your own. Everything runs in the namespace "mesh", deleted at the end.
#   ./run.sh                                      everything, then the API checks
#   ./run.sh balance identity resilience overhead
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C57_SOURCES:-build/src}
KUBECTL=${KUBECTL:-../../ch52-health-checks-actuator/verify/build/k3s/k3s kubectl}
./build-libs.sh
CP=build/main:build/tools
echo "== sidecar configurations, validated by Envoy"
"$JAVA_HOME/bin/java" -cp $CP -Dc57.certs=build/certs Measure configs build/envoy
for f in build/envoy/*.yaml; do printf '%-28s ' "$(basename "$f")"; build/bin/envoy --mode validate -c "$f" -l error 2>&1 | tail -1; done

"$JAVA_HOME/bin/java" -version 2>&1 | head -1
$KUBECTL version 2>/dev/null | grep -i server || true
build/bin/envoy --version | sed 's/^.*version: //'
"$JAVA_HOME/bin/java" -cp $CP -Dc57.kubectl="$KUBECTL" -Dc57.certs=build/certs Measure ${*:-balance identity resilience overhead}

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
