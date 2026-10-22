#!/usr/bin/env bash
# Builds chapter 58's menu-service image (DeliveryLab on chapter 54's jlink'd Java 25 runtime; no base image is
# pulled) and loads it into the cluster; kustomize from chapter 55's build; and sparse clones of kubectl 1.37.1,
# Gateway API 1.6.1 and Argo CD 3.5.5 for check_apis.sh. Needs docker, git and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
SRC=${C58_SOURCES:-build/src}
LOAD_IMAGE=${LOAD_IMAGE:-../../ch52-health-checks-actuator/verify/build/k3s/k3s ctr images import -}
mkdir -p "$SRC"
clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/kubernetes/kubernetes v1.37.1 "$SRC/k8s" /staging/src/k8s.io/kubectl/pkg/cmd/diff/diff.go \
  /staging/src/k8s.io/kubectl/pkg/cmd/apply/apply.go
clone https://github.com/kubernetes-sigs/gateway-api v1.6.1 "$SRC/gateway-api" /apis/v1/shared_types.go
clone https://github.com/argoproj/argo-cd v3.5.5 "$SRC/argo-cd" /cmd/argocd-application-controller/commands/argocd_application_controller.go \
  /docs/user-guide/auto_sync.md
[ -n "${KUSTOMIZE:-}" ] || ../../ch55-helm-kustomize/verify/build-libs.sh > /dev/null
../../ch54-kubernetes-for-spring/verify/build-libs.sh > /dev/null      # the c54/jre:25 runtime image
rm -rf build/main build/tools build/image && mkdir -p build/main build/tools build/image/app
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp build/main tools/*.java
cp build/tools/DeliveryLab.class build/image/app/
printf 'FROM c54/jre:25\nCOPY app/ /app/\nENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-cp", "/app", "DeliveryLab"]\n' > build/image/Dockerfile
docker build -q -t foodfleet/menu-service:ch58 build/image > /dev/null
if [[ "$LOAD_IMAGE" == *" -" ]]; then docker save foodfleet/menu-service:ch58 | ${LOAD_IMAGE} > /dev/null 2>&1
else ${LOAD_IMAGE} foodfleet/menu-service:ch58 > /dev/null; fi
