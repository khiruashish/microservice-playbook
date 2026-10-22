#!/usr/bin/env bash
# Chapter 56 measurements: a real HorizontalPodAutoscaler scaling menu-service under load. About 40 minutes. Needs
# docker, a JDK 25 (JAVA_HOME) and a cluster with metrics-server:
# K3S_DISABLE=traefik ../../ch52-health-checks-actuator/verify/k3s-lab.sh starts one, or set KUBECTL (and LOAD_IMAGE,
# e.g. "kind load docker-image") for your own. Results depend on the node's CPUs: the lab had 2.
#   ./run.sh                     everything, then the API checks
#   ./run.sh step warmup flap
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C56_SOURCES:-build/src}
KUBECTL=${KUBECTL:-../../ch52-health-checks-actuator/verify/build/k3s/k3s kubectl}
./build-libs.sh
$KUBECTL top nodes > /dev/null 2>&1 || { echo "metrics-server isn't serving metrics: the HPA needs it"; exit 1; }

"$JAVA_HOME/bin/java" -version 2>&1 | head -1
$KUBECTL version 2>/dev/null | grep -i server || true
MODES=${*:-step warmup flap}
for m in $MODES; do
  echo "== $m"
  "$JAVA_HOME/bin/java" -cp build/tools -Dc56.kubectl="$KUBECTL" -Dc56.k8s=../src/main/resources/k8s Measure "$m"
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
