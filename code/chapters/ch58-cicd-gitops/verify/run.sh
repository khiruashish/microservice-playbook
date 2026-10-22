#!/usr/bin/env bash
# Chapter 58 measurements: a GitOps reconciler, canaries through the Gateway API, and a blue/green switch, on a real
# cluster. About 20 minutes. Needs docker, git, a JDK 25 (JAVA_HOME) and a cluster with Traefik:
# K3S_DISABLE= ../../ch52-health-checks-actuator/verify/k3s-lab.sh starts one with everything k3s bundles, or set
# KUBECTL (and LOAD_IMAGE) for your own cluster with any Gateway API implementation named traefik-gateway.
#   ./run.sh                          everything, then the API checks
#   ./run.sh gitops canary bluegreen
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C58_SOURCES:-build/src}
KUBECTL=${KUBECTL:-../../ch52-health-checks-actuator/verify/build/k3s/k3s kubectl}
./build-libs.sh
KUSTOMIZE=${KUSTOMIZE:-$PWD/../../ch55-helm-kustomize/verify/build/bin/kustomize}
# Traefik's Gateway API provider and its Gateway (k3s's Helm controller upgrades the bundled chart)
$KUBECTL apply -f ../src/main/resources/delivery/traefik-gateway.yaml > /dev/null
for i in $(seq 1 90); do
  [ "$($KUBECTL -n kube-system get gateway traefik-gateway -o jsonpath='{.status.conditions[?(@.type=="Programmed")].status}' 2>/dev/null)" = True ] && break
  sleep 2
done

"$JAVA_HOME/bin/java" -version 2>&1 | head -1
$KUBECTL version 2>/dev/null | grep -i server || true
$KUBECTL -n kube-system get deploy traefik -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
"$JAVA_HOME/bin/java" -cp build/main:build/tools -Dc58.kubectl="$KUBECTL" -Dc58.kustomize="$KUSTOMIZE" \
  -Dc58.resources=../src/main/resources -Dc58.work="$PWD/build/work" Measure ${*:-gitops canary bluegreen}

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
