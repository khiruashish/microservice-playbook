#!/usr/bin/env bash
# Builds chapter 56's menu-service image (ScaleLab on chapter 54's jlink'd Java 25 runtime; no base image is pulled),
# loads it into the cluster, and makes sparse clones of Kubernetes 1.37.1 and metrics-server 0.9.0 for check_apis.sh.
# Needs docker, git and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
SRC=${C56_SOURCES:-build/src}
LOAD_IMAGE=${LOAD_IMAGE:-../../ch52-health-checks-actuator/verify/build/k3s/k3s ctr images import -}
mkdir -p "$SRC"
clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/kubernetes/kubernetes v1.37.1 "$SRC/k8s" /pkg/controller/podautoscaler/replica_calculator.go \
  /pkg/controller/podautoscaler/horizontal.go /pkg/controller/podautoscaler/config/v1alpha1/defaults.go /pkg/apis/autoscaling/v2/defaults.go
clone https://github.com/kubernetes-sigs/metrics-server v0.9.0 "$SRC/metrics-server" /cmd/metrics-server/app/options/options.go
../../ch54-kubernetes-for-spring/verify/build-libs.sh > /dev/null      # the c54/jre:25 runtime image
rm -rf build/tools build/image && mkdir -p build/tools build/image/app
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools tools/ScaleLab.java tools/Measure.java
cp build/tools/ScaleLab.class build/image/app/
printf 'FROM c54/jre:25\nCOPY app/ /app/\nENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-cp", "/app", "ScaleLab"]\n' > build/image/Dockerfile
docker build -q -t foodfleet/menu-service:ch56 build/image > /dev/null
if [[ "$LOAD_IMAGE" == *" -" ]]; then docker save foodfleet/menu-service:ch56 | ${LOAD_IMAGE} > /dev/null 2>&1
else ${LOAD_IMAGE} foodfleet/menu-service:ch56 > /dev/null; fi
