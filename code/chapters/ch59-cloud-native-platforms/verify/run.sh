#!/usr/bin/env bash
# Chapter 59 measurements. About 25 minutes. Needs docker, git, a JDK 25 (JAVA_HOME) and, for identity, a cluster:
# ../../ch52-health-checks-actuator/verify/k3s-lab.sh starts one, or set KUBECTL (and LOAD_IMAGE) for your own.
# throttling and serverless need no cluster.
#   ./run.sh                               everything, then the API checks
#   ./run.sh identity throttling serverless
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C59_SOURCES:-build/src}
KUBECTL=${KUBECTL:-../../ch52-health-checks-actuator/verify/build/k3s/k3s kubectl}
./build-libs.sh

"$JAVA_HOME/bin/java" -version 2>&1 | head -1
$KUBECTL version 2>/dev/null | grep -i server || true
"$JAVA_HOME/bin/java" -cp build/main:build/tools -Dc59.kubectl="$KUBECTL" -Dc59.k8s=../src/main/resources/k8s \
  -Dc59.image=foodfleet/menu-service:ch54 Measure ${*:-identity throttling serverless}

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
