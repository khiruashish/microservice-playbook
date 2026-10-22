#!/usr/bin/env bash
# Chapter 55 measurements: one service rendered and deployed with Helm 4.1.4 and kustomize 5.8.3 on a real cluster,
# pulling from an OCI registry. About 15 minutes. Needs docker, a JDK 25 (JAVA_HOME), a cluster
# (../../ch52-health-checks-actuator/verify/k3s-lab.sh) and a registry it pulls from (./registry-lab.sh), or set
# KUBECTL, REGISTRY and RMI (a command that removes an image from the node, e.g. "docker exec kind-control-plane crictl rmi").
#   ./run.sh                                       everything, then the API checks
#   ./run.sh render ownership config rollback tags
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}"
SRC=${C55_SOURCES:-build/src}
K3S=../../ch52-health-checks-actuator/verify/build/k3s/k3s
KUBECTL=${KUBECTL:-$K3S kubectl}
RMI=${RMI:-$K3S crictl rmi}
REGISTRY=${REGISTRY:-registry.foodfleet.local:5000}
[ -z "${KUBECONFIG:-}" ] && [ -r /etc/rancher/k3s/k3s.yaml ] && export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
./build-libs.sh
HELM=${HELM:-$PWD/build/bin/helm}
KUSTOMIZE=${KUSTOMIZE:-$PWD/build/bin/kustomize}
CRANE=$PWD/../../ch53-containerization/verify/build/bin/crane
export NO_PROXY="${NO_PROXY:-},${REGISTRY%%:*}" no_proxy="${no_proxy:-},${REGISTRY%%:*}"
# push both versions; from here on, everything refers to them by digest
for v in 1.4.0 1.4.1; do
  docker save "foodfleet/menu-service:$v" -o "build/menu-$v.tar"
  "$CRANE" push --insecure "build/menu-$v.tar" "$REGISTRY/menu-service:$v" > /dev/null
done
D1=$("$CRANE" digest --insecure "$REGISTRY/menu-service:1.4.0")
D2=$("$CRANE" digest --insecure "$REGISTRY/menu-service:1.4.1")
echo "menu-service 1.4.0 = $D1"
echo "menu-service 1.4.1 = $D2"
rm -rf build/main && mkdir -p build/main
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp build/main tools/Measure.java

"$JAVA_HOME/bin/java" -version 2>&1 | head -1
$KUBECTL version 2>/dev/null | grep -i server || true
"$HELM" version --short; "$KUSTOMIZE" version
MODES=${*:-render ownership config rollback tags}
for m in $MODES; do
  echo "== $m"
  "$JAVA_HOME/bin/java" -cp build/main:build/tools -Dc55.kubectl="$KUBECTL" -Dc55.helm="$HELM" -Dc55.kustomize="$KUSTOMIZE" \
    ${KUBECONFIG:+-Dc55.kubeconfig="$KUBECONFIG"} \
    -Dc55.crane="$CRANE" -Dc55.rmi="$RMI" -Dc55.registry="$REGISTRY" -Dc55.digest.v1="$D1" -Dc55.digest.v2="$D2" \
    -Dc55.chart=../src/main/resources/helm/foodfleet-service -Dc55.kustomize.dir=../src/main/resources/kustomize -Dc55.work="$PWD/build/work" \
    Measure "$m"
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
